param(
    [string] $JavaExe,
    [string] $MavenExe,
    [switch] $SkipPackage,
    [switch] $KeepRunning,
    [int] $StartupTimeoutSeconds = 90,
    [int] $PostgresPort = 15438,
    [int] $InstanceAPort = 18089,
    [int] $InstanceBPort = 18090,
    [int] $RequestCount = 40,
    [int] $RejectEvery = 5
)

$ErrorActionPreference = "Stop"

Write-Host "IntentHub basic multi-instance stress"

$root = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $root "intent-hub-interfaces/target/intent-hub-interfaces-0.1.0-SNAPSHOT.jar"
$logDir = Join-Path $root "target/smoke"
$pgContainer = "intent-hub-basic-stress-postgres"
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
    $stdoutLog = Join-Path $logDir "intent-hub-basic-stress-$Name.out.log"
    $stderrLog = Join-Path $logDir "intent-hub-basic-stress-$Name.err.log"
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

function Invoke-StressRecognitionJob {
    param(
        [int] $Port,
        [string] $TenantId,
        [string] $SceneId,
        [int] $Index,
        [int] $RejectEvery
    )
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $reject = $RejectEvery -gt 0 -and $Index % $RejectEvery -eq 0
    $body = @{
        tenantId = $TenantId
        source = "stress"
        channel = "basic-multi-instance-stress"
        inputType = "TEXT"
        requestId = "req-basic-stress-$Index"
        traceId = "trace-basic-stress-$Index"
        text = if ($reject) { "unmatched stress text $Index" } else { "cancel A$Index" }
        metadata = @{ scene_id = $SceneId }
        attachments = @()
    } | ConvertTo-Json -Depth 10 -Compress
    try {
        $response = Invoke-RestMethod -Method Post -Uri "http://localhost:$Port/api/v1/intent/recognize" -ContentType "application/json" -Body $body -TimeoutSec 15
        $stopwatch.Stop()
        [pscustomobject]@{
            ok = $true
            latencyMs = $stopwatch.ElapsedMilliseconds
            intentCode = $response.intentCode
            decision = $response.decision
            recognitionPath = $response.recognitionPath
            error = $null
        }
    } catch {
        $stopwatch.Stop()
        [pscustomobject]@{
            ok = $false
            latencyMs = $stopwatch.ElapsedMilliseconds
            intentCode = $null
            decision = "ERROR"
            recognitionPath = @()
            error = $_.Exception.Message
        }
    }
}

function Percentile {
    param(
        [long[]] $Values,
        [double] $Quantile
    )
    if (-not $Values -or $Values.Count -eq 0) {
        return 0
    }
    $sorted = @($Values | Sort-Object)
    $index = [Math]::Ceiling($Quantile * $sorted.Count) - 1
    $index = [Math]::Max(0, [Math]::Min($index, $sorted.Count - 1))
    return $sorted[$index]
}

try {
    if ($RequestCount -le 0) {
        Fail "RequestCount must be positive"
    }
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

    $tenantId = "tenant-basic-stress"
    $sceneId = "basic-stress-scene"
    $version = "v-basic-stress"

    Invoke-IntentHubJson -Method Post -Uri "http://localhost:$InstanceAPort/api/v1/admin/config/versions" -Body @{
        tenantId = $tenantId
        sceneId = $sceneId
        version = $version
        description = "basic multi-instance stress"
        actor = "basic-stress"
    } | Out-Null

    Invoke-IntentHubJson -Method Post -Uri "http://localhost:$InstanceAPort/api/v1/admin/config/versions/$version/intents?tenantId=$tenantId&sceneId=$sceneId" -Body @{
        actor = "basic-stress"
        payload = @{
            intentCode = "ORDER_CANCEL"
            intentName = "Basic stress order cancel"
            enabled = $true
            definition = @{
                matchType = "CONTAINS"
                pattern = "cancel"
                confidence = 0.91
                explanation = "basic stress rule"
            }
        }
    } | Out-Null

    Invoke-IntentHubJson -Method Post -Uri "http://localhost:$InstanceAPort/api/v1/admin/config/versions/$version/downstream-actions?tenantId=$tenantId&sceneId=$sceneId" -Body @{
        actor = "basic-stress"
        payload = @{
            actionCode = "ORDER_CANCEL_COMMAND"
            actionType = "MQ"
            target = "order.cancel"
            idempotencyRequired = $false
            timeoutMs = 1500
            actionSchema = @{ intentCode = "ORDER_CANCEL" }
        }
    } | Out-Null

    Invoke-IntentHubJson -Method Post -Uri "http://localhost:$InstanceAPort/api/v1/admin/config/versions/$version/strategies?tenantId=$tenantId&sceneId=$sceneId" -Body @{
        actor = "basic-stress"
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
        actor = "basic-stress"
    } | Out-Null
    Ok "basic stress config published"

    $jobs = @()
    for ($i = 1; $i -le $RequestCount; $i++) {
        $port = if ($i % 2 -eq 0) { $InstanceAPort } else { $InstanceBPort }
        $jobs += Start-Job -ScriptBlock ${function:Invoke-StressRecognitionJob} -ArgumentList $port, $tenantId, $sceneId, $i, $RejectEvery
    }
    Wait-Job -Job $jobs | Out-Null
    $results = @()
    foreach ($job in $jobs) {
        $results += Receive-Job -Job $job
        if ($job.State -ne "Completed") {
            Fail "stress job failed: $($job.State)"
        }
    }
    Remove-Job -Job $jobs -Force

    $errors = @($results | Where-Object { -not $_.ok }).Count
    $successes = @($results | Where-Object { $_.ok -and $_.decision -eq "SUCCESS" }).Count
    $rejected = @($results | Where-Object { $_.ok -and $_.decision -eq "REJECTED" }).Count
    $fallbacks = @($results | Where-Object { $_.recognitionPath -and (($_.recognitionPath -join ",").Contains("FALLBACK")) }).Count
    $latencies = @($results | ForEach-Object { [long]$_.latencyMs })
    $avg = [Math]::Round((($latencies | Measure-Object -Average).Average), 2)
    $p95 = Percentile -Values $latencies -Quantile 0.95
    $p99 = Percentile -Values $latencies -Quantile 0.99

    $expectedRejected = if ($RejectEvery -le 0) { 0 } else { [Math]::Floor($RequestCount / $RejectEvery) }
    $expectedSuccesses = $RequestCount - $expectedRejected
    if ($errors -ne 0) {
        $actual = $results | ConvertTo-Json -Depth 20 -Compress
        Fail "stress requests should not have transport errors; results=$actual"
    }
    if ($successes -ne $expectedSuccesses -or $rejected -ne $expectedRejected) {
        $actual = $results | ConvertTo-Json -Depth 20 -Compress
        Fail "unexpected decision counts: successes=$successes rejected=$rejected expectedSuccesses=$expectedSuccesses expectedRejected=$expectedRejected results=$actual"
    }

    $traceCount = Invoke-Postgres "select count(*) from recognition_trace where tenant_id = '$tenantId' and scene_id = '$sceneId';"
    $badCaseCount = Invoke-Postgres "select count(*) from bad_case where tenant_id = '$tenantId' and scene_id = '$sceneId';"
    if ([int]$traceCount -ne $RequestCount) {
        Fail "recognition_trace count should match request count; actual: $traceCount"
    }
    if ([int]$badCaseCount -ne $expectedRejected) {
        Fail "bad_case count should match rejected count; actual: $badCaseCount"
    }

    Write-Host "[INFO] requests=$RequestCount successes=$successes rejected=$rejected fallbacks=$fallbacks errors=$errors"
    Write-Host "[INFO] latency_ms avg=$avg p95=$p95 p99=$p99"
    Write-Host "[INFO] db recognition_trace=$traceCount bad_case=$badCaseCount"
    Write-Host "[OK] basic multi-instance stress completed"
} finally {
    Stop-ProcessIfRunning -Process $script:appAProcess -Name "Intent Hub instance a"
    Stop-ProcessIfRunning -Process $script:appBProcess -Name "Intent Hub instance b"
    Stop-Postgres
}
