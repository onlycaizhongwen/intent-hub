param(
    [string] $JavaExe,
    [string] $MavenExe,
    [switch] $SkipPackage,
    [switch] $KeepRunning,
    [int] $StartupTimeoutSeconds = 90,
    [int] $PostgresPort = 15435,
    [int] $InstanceAPort = 18082,
    [int] $InstanceBPort = 18083
)

$ErrorActionPreference = "Stop"

Write-Host "IntentHub multi-instance consistency smoke"

$root = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $root "intent-hub-interfaces/target/intent-hub-interfaces-0.1.0-SNAPSHOT.jar"
$logDir = Join-Path $root "target/smoke"
$pgContainer = "intent-hub-multi-instance-postgres"
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
        [string] $Uri,
        [object] $Body = $null
    )

    $headers = @{ "Content-Type" = "application/json" }
    if ($null -eq $Body) {
        return Invoke-RestMethod -Method $Method -Uri $Uri -Headers $headers -TimeoutSec 10
    }
    $json = $Body | ConvertTo-Json -Depth 20 -Compress
    return Invoke-RestMethod -Method $Method -Uri $Uri -Headers $headers -Body $json -TimeoutSec 10
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
    $stdoutLog = Join-Path $logDir "intent-hub-multi-instance-$Name.out.log"
    $stderrLog = Join-Path $logDir "intent-hub-multi-instance-$Name.err.log"
    $arguments = @(
        "-jar", $jarPath,
        "--server.port=$Port",
        "--spring.profiles.active=local-jdbc",
        "--spring.datasource.url=jdbc:postgresql://localhost:$PostgresPort/intent_hub",
        "--intent-hub.model-service.enabled=false",
        "--intent-hub.llm.enabled=false"
    )
    $process = Start-Process -FilePath $Java -ArgumentList $arguments -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog -PassThru
    Ok "Intent Hub instance $Name started: port=$Port pid=$($process.Id)"
    return $process
}

function Stop-App {
    param([System.Diagnostics.Process] $Process, [string] $Name)
    if ($Process -and -not $Process.HasExited) {
        Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue
        Ok "Intent Hub instance $Name stopped"
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

function Invoke-Recognition {
    param(
        [int] $Port,
        [string] $TenantId,
        [string] $SceneId,
        [string] $RequestId,
        [string] $TraceId
    )
    $body = @{
        tenantId = $TenantId
        source = "smoke"
        channel = "multi-instance"
        inputType = "TEXT"
        requestId = $RequestId
        traceId = $TraceId
        text = "cancel A100"
        metadata = @{ scene_id = $SceneId }
        attachments = @()
    }
    return Invoke-IntentHubJson -Method Post -Uri "http://localhost:$Port/api/v1/intent/recognize" -Body $body
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

    $flywayVersions = Invoke-Postgres "select string_agg(version, ',' order by installed_rank) from flyway_schema_history where success = true;"
    if ($flywayVersions -ne "1,2,3") {
        Fail "Flyway versions are not 1,2,3; actual: $flywayVersions"
    }
    Ok "Flyway applied V1/V2/V3"

    $tenantId = "tenant-multi-instance-smoke"
    $sceneId = "multi-instance-smoke-scene"
    $version = "v-multi-instance-smoke"
    $requestId = "req-multi-instance-idempotent"
    $actionCode = "ORDER_CANCEL_COMMAND"

    Invoke-IntentHubJson -Method Post -Uri "http://localhost:$InstanceAPort/api/v1/admin/config/versions" -Body @{
        tenantId = $tenantId
        sceneId = $sceneId
        version = $version
        description = "multi-instance consistency smoke"
        actor = "multi-instance-smoke"
    } | Out-Null

    Invoke-IntentHubJson -Method Post -Uri "http://localhost:$InstanceAPort/api/v1/admin/config/versions/$version/intents?tenantId=$tenantId&sceneId=$sceneId" -Body @{
        actor = "multi-instance-smoke"
        payload = @{
            intentCode = "ORDER_CANCEL"
            intentName = "Multi instance order cancel smoke"
            enabled = $true
            definition = @{
                matchType = "CONTAINS"
                pattern = "cancel"
                confidence = 0.91
                explanation = "multi instance smoke rule"
            }
        }
    } | Out-Null

    Invoke-IntentHubJson -Method Post -Uri "http://localhost:$InstanceAPort/api/v1/admin/config/versions/$version/downstream-actions?tenantId=$tenantId&sceneId=$sceneId" -Body @{
        actor = "multi-instance-smoke"
        payload = @{
            actionCode = $actionCode
            actionType = "MQ"
            target = "order.cancel"
            idempotencyRequired = $true
            timeoutMs = 1500
            actionSchema = @{ intentCode = "ORDER_CANCEL" }
        }
    } | Out-Null

    Invoke-IntentHubJson -Method Post -Uri "http://localhost:$InstanceAPort/api/v1/admin/config/versions/$version/strategies?tenantId=$tenantId&sceneId=$sceneId" -Body @{
        actor = "multi-instance-smoke"
        payload = @{
            strategyCode = "default"
            confidenceThreshold = 0.60
            llmPolicy = @{}
            modelPolicy = @{ enabled = $false }
        }
    } | Out-Null

    $validation = Invoke-IntentHubJson -Method Post -Uri "http://localhost:$InstanceAPort/api/v1/admin/config/versions/$version/validate?tenantId=$tenantId&sceneId=$sceneId"
    if (-not $validation.valid) {
        Fail "config validation failed: $($validation.errors -join '; ')"
    }
    Invoke-IntentHubJson -Method Post -Uri "http://localhost:$InstanceAPort/api/v1/admin/config/versions/$version/publish?tenantId=$tenantId&sceneId=$sceneId" -Body @{
        actor = "multi-instance-smoke"
    } | Out-Null
    Ok "multi-instance smoke config published by instance A"

    $resultA = Invoke-Recognition -Port $InstanceAPort -TenantId $tenantId -SceneId $sceneId -RequestId $requestId -TraceId "trace-multi-instance-a"
    if ($resultA.intentCode -ne "ORDER_CANCEL" -or $resultA.decision -ne "ASYNC_ACCEPTED") {
        Fail "instance A recognition unexpected: intent=$($resultA.intentCode), decision=$($resultA.decision)"
    }
    if ([string]::IsNullOrWhiteSpace($resultA.idempotencyKey)) {
        Fail "instance A idempotency key unexpected: $($resultA.idempotencyKey)"
    }
    Ok "instance A recognition returned ORDER_CANCEL/ASYNC_ACCEPTED"

    $resultB = Invoke-Recognition -Port $InstanceBPort -TenantId $tenantId -SceneId $sceneId -RequestId $requestId -TraceId "trace-multi-instance-b"
    if ($resultB.intentCode -ne "ORDER_CANCEL" -or $resultB.decision -ne "ASYNC_ACCEPTED") {
        Fail "instance B recognition unexpected: intent=$($resultB.intentCode), decision=$($resultB.decision)"
    }
    if ([string]::IsNullOrWhiteSpace($resultB.idempotencyKey)) {
        Fail "instance B idempotency key unexpected: $($resultB.idempotencyKey)"
    }
    if ($resultA.idempotencyKey -ne $resultB.idempotencyKey) {
        Fail "idempotency keys differ across instances"
    }
    Ok "instance B recognition returned the same idempotency key"

    $sharedIdempotencyKey = $resultA.idempotencyKey
    $idempotencyCount = Invoke-Postgres "select count(*) from idempotency_record where idempotency_key = '$sharedIdempotencyKey';"
    if ($idempotencyCount -ne "1") {
        Fail "idempotency_record count should be 1; actual: $idempotencyCount"
    }
    Ok "idempotency_record has exactly one row for the shared key"

    $traceCount = Invoke-Postgres "select count(*) from recognition_trace where tenant_id = '$tenantId' and request_id = '$requestId' and idempotency_key = '$sharedIdempotencyKey';"
    if ($traceCount -ne "2") {
        Fail "recognition_trace count should be 2; actual: $traceCount"
    }
    Ok "recognition_trace has one row per instance request"

    Write-Host "[OK] multi-instance consistency smoke completed"
} finally {
    Stop-App -Process $script:appAProcess -Name "a"
    Stop-App -Process $script:appBProcess -Name "b"
    Stop-Postgres
}
