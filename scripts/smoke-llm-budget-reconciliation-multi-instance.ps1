param(
    [string] $JavaExe,
    [string] $MavenExe,
    [switch] $SkipPackage,
    [switch] $KeepRunning,
    [int] $StartupTimeoutSeconds = 90,
    [int] $PostgresPort = 15437,
    [int] $InstanceAPort = 18087,
    [int] $InstanceBPort = 18088
)

$ErrorActionPreference = "Stop"

Write-Host "IntentHub LLM budget reconciliation multi-instance smoke"

$root = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $root "intent-hub-interfaces/target/intent-hub-interfaces-0.1.0-SNAPSHOT.jar"
$logDir = Join-Path $root "target/smoke"
$pgContainer = "intent-hub-llm-budget-reconciliation-postgres"
$appAProcess = $null
$appBProcess = $null

function Fail {
    param([string] $Message)
    Write-Host "[FAIL] $Message"
    exit 1
}

function Ok {
    param([string] $Message)
    Write-Host "[OK] $Message"
}

function Resolve-Java {
    if ($JavaExe -and (Test-Path $JavaExe)) {
        return $JavaExe
    }
    $adoptium = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\java.exe"
    if (Test-Path $adoptium) {
        return $adoptium
    }
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin/java.exe"
        if (Test-Path $candidate) {
            return $candidate
        }
    }
    $command = Get-Command java -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }
    Fail "java command not found"
}

function Resolve-Maven {
    if ($MavenExe -and (Test-Path $MavenExe)) {
        return $MavenExe
    }
    $command = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }
    $ideaMaven = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd"
    if (Test-Path $ideaMaven) {
        return $ideaMaven
    }
    Fail "mvn.cmd command not found"
}

function Use-JavaForChildProcesses {
    param([string] $Path)
    $javaBin = Split-Path -Parent $Path
    $javaHome = Split-Path -Parent $javaBin
    $env:JAVA_HOME = $javaHome
    $env:PATH = "$javaBin;$env:PATH"
}

function Assert-Port-Free {
    param([int] $Port)
    $connection = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue
    if ($connection) {
        Fail "local port $Port is already in use"
    }
}

function Wait-Postgres {
    param([int] $TimeoutSeconds)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        & docker exec $pgContainer pg_isready -U intent_hub -d intent_hub | Out-Null
        if ($LASTEXITCODE -eq 0) {
            return
        }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    Fail "timeout waiting for PostgreSQL"
}

function Wait-HttpJson {
    param(
        [string] $Uri,
        [int] $TimeoutSeconds
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            return Invoke-RestMethod -Method Get -Uri $Uri -TimeoutSec 3
        } catch {
            Start-Sleep -Seconds 1
        }
    } while ((Get-Date) -lt $deadline)
    Fail "timeout waiting for $Uri"
}

function Invoke-IntentHubJson {
    param(
        [string] $Method,
        [string] $Uri
    )
    return Invoke-RestMethod -Method $Method -Uri $Uri -Headers @{ "Content-Type" = "application/json" } -TimeoutSec 10
}

function Invoke-Postgres {
    param([string] $Sql)
    $output = & docker exec $pgContainer psql -U intent_hub -d intent_hub -t -A -c $Sql 2>&1
    if ($LASTEXITCODE -ne 0) {
        Fail "psql failed: $output"
    }
    return (($output -join "`n").Trim())
}

function Start-IntentHubInstance {
    param(
        [string] $Name,
        [int] $Port,
        [string] $Java
    )
    $stdoutLog = Join-Path $logDir "intent-hub-llm-reconciliation-$Name.out.log"
    $stderrLog = Join-Path $logDir "intent-hub-llm-reconciliation-$Name.err.log"
    $arguments = @(
        "-jar", $jarPath,
        "--server.port=$Port",
        "--spring.profiles.active=local-jdbc",
        "--spring.datasource.url=jdbc:postgresql://localhost:$PostgresPort/intent_hub",
        "--intent-hub.model-service.enabled=false",
        "--intent-hub.llm.enabled=false",
        "--intent-hub.llm.budget-reconciliation.enabled=true",
        "--intent-hub.llm.budget-reconciliation.stale-after=PT1S",
        "--intent-hub.llm.budget-reconciliation.interval=PT1S"
    )
    $process = Start-Process -FilePath $Java -ArgumentList $arguments -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog -PassThru
    Ok "Intent Hub instance $Name started: port=$Port pid=$($process.Id)"
    return $process
}

function Stop-ProcessIfRunning {
    param([System.Diagnostics.Process] $Process, [string] $Name)
    if ($Process -and -not $Process.HasExited) {
        Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue
        Ok "$Name stopped"
    }
}

function Stop-Postgres {
    if (-not $KeepRunning) {
        $existing = & docker ps -aq --filter "name=^/$pgContainer$"
        if ($existing) {
            & docker rm -f $pgContainer | Out-Null
            Ok "PostgreSQL container removed"
        }
    } else {
        Write-Host "[WARN] KeepRunning is enabled; PostgreSQL container is still running"
    }
}

function Insert-StaleBudgetRows {
    param([string] $TenantId, [string] $SceneId)
    $today = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd")
    Invoke-Postgres "insert into llm_budget_usage (tenant_id, scene_id, usage_date, provider, model, attempt_count, consumed_units, created_at, updated_at) values ('$TenantId', '$SceneId', date '$today', 'http-contract', 'mock-llm', 1, 1.0, now() - interval '10 minutes', now() - interval '10 minutes');" | Out-Null
    Invoke-Postgres "insert into llm_budget_usage (tenant_id, scene_id, usage_date, provider, model, attempt_count, consumed_units, created_at, updated_at) values ('$TenantId', '$SceneId', date '$today', '__budget__', '__daily__', 2, 2.0, now() - interval '10 minutes', now() - interval '10 minutes');" | Out-Null
}

function Get-PendingUnits {
    param([object] $Usage)
    return [Math]::Max(0.0, [double]$Usage.reservedUnits - [double]$Usage.consumedUnits)
}

function Wait-Reconciliation {
    param(
        [string] $TenantId,
        [string] $SceneId,
        [int] $TimeoutSeconds
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $usage = Invoke-IntentHubJson -Method Get -Uri "http://localhost:$InstanceAPort/api/v1/admin/llm/budget-usage?tenantId=$TenantId&sceneId=$SceneId"
        $metricsA = Invoke-IntentHubJson -Method Get -Uri "http://localhost:$InstanceAPort/api/v1/admin/metrics"
        $metricsB = Invoke-IntentHubJson -Method Get -Uri "http://localhost:$InstanceBPort/api/v1/admin/metrics"
        $reconciliations = [long]$metricsA.totalLlmBudgetReconciliations + [long]$metricsB.totalLlmBudgetReconciliations
        $pendingUnits = Get-PendingUnits -Usage $usage
        if ($pendingUnits -eq 0.0 -and [double]$usage.reservedUnits -eq 1.0 -and $reconciliations -ge 1) {
            return @{
                usage = $usage
                reconciliations = $reconciliations
            }
        }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    Fail "timeout waiting for stale budget reconciliation"
}

try {
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    Assert-Port-Free $PostgresPort
    Assert-Port-Free $InstanceAPort
    Assert-Port-Free $InstanceBPort

    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $docker) {
        Fail "docker command not found"
    }
    Ok "docker command found"

    $existing = & docker ps -aq --filter "name=^/$pgContainer$"
    if ($existing) {
        & docker rm -f $pgContainer | Out-Null
    }
    & docker run --name $pgContainer `
        -e POSTGRES_DB=intent_hub `
        -e POSTGRES_USER=intent_hub `
        -e POSTGRES_PASSWORD=intent_hub `
        -p "${PostgresPort}:5432" `
        -d postgres:16-alpine | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Fail "failed to start PostgreSQL container"
    }
    Ok "PostgreSQL container started"
    Wait-Postgres -TimeoutSeconds $StartupTimeoutSeconds
    Ok "PostgreSQL is ready"

    $java = Resolve-Java
    Use-JavaForChildProcesses -Path $java
    Ok "Java detected: $java"

    if (-not $SkipPackage) {
        $maven = Resolve-Maven
        Ok "Maven detected: $maven"
        Push-Location $root
        try {
            & $maven package -DskipTests | Out-Host
            if ($LASTEXITCODE -ne 0) {
                Fail "mvn package -DskipTests exited with code $LASTEXITCODE"
            }
        } finally {
            Pop-Location
        }
        Ok "Intent Hub jar packaged"
    } elseif (-not (Test-Path $jarPath)) {
        Fail "jar not found: $jarPath"
    }

    $script:appAProcess = Start-IntentHubInstance -Name "a" -Port $InstanceAPort -Java $java
    $healthA = Wait-HttpJson -Uri "http://localhost:$InstanceAPort/api/v1/admin/health" -TimeoutSeconds $StartupTimeoutSeconds
    if ($healthA.status -ne "UP") {
        Fail "Intent Hub instance A health is not UP"
    }
    Ok "Intent Hub instance A health is UP"

    $script:appBProcess = Start-IntentHubInstance -Name "b" -Port $InstanceBPort -Java $java
    $healthB = Wait-HttpJson -Uri "http://localhost:$InstanceBPort/api/v1/admin/health" -TimeoutSeconds $StartupTimeoutSeconds
    if ($healthB.status -ne "UP") {
        Fail "Intent Hub instance B health is not UP"
    }
    Ok "Intent Hub instance B health is UP"

    $tenantId = "tenant-llm-reconciliation-smoke"
    $sceneId = "llm-reconciliation-smoke-scene"
    Insert-StaleBudgetRows -TenantId $tenantId -SceneId $sceneId
    Ok "stale pending budget rows inserted"

    $before = Invoke-IntentHubJson -Method Get -Uri "http://localhost:$InstanceAPort/api/v1/admin/llm/budget-usage?tenantId=$tenantId&sceneId=$sceneId"
    if ((Get-PendingUnits -Usage $before) -ne 1.0) {
        Fail "pending units before reconciliation should be 1.0: $($before | ConvertTo-Json -Compress)"
    }

    $result = Wait-Reconciliation -TenantId $tenantId -SceneId $sceneId -TimeoutSeconds $StartupTimeoutSeconds
    $usage = $result.usage
    if ([double]$usage.consumedUnits -ne 1.0 -or [double]$usage.reservedUnits -ne 1.0 -or (Get-PendingUnits -Usage $usage) -ne 0.0) {
        Fail "budget usage was not reconciled to confirmed usage: $($usage | ConvertTo-Json -Compress)"
    }
    Ok "stale pending budget was reconciled"

    $budgetDetails = Invoke-Postgres "select provider || '/' || model || ':' || attempt_count || ':' || consumed_units from llm_budget_usage where tenant_id = '$tenantId' and scene_id = '$sceneId' order by provider, model;"
    Write-Host "[INFO] budget rows: $budgetDetails"
    Write-Host "[INFO] reconciliation metric total across instances: $($result.reconciliations)"
    Write-Host "[OK] LLM budget reconciliation multi-instance smoke completed"
} finally {
    Stop-ProcessIfRunning -Process $script:appAProcess -Name "Intent Hub instance a"
    Stop-ProcessIfRunning -Process $script:appBProcess -Name "Intent Hub instance b"
    Stop-Postgres
}
