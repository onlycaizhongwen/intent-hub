param(
    [string] $JavaExe,
    [string] $MavenExe,
    [switch] $SkipPackage,
    [switch] $KeepRunning,
    [int] $PostgresPort = 15432,
    [int] $StartupTimeoutSeconds = 90
)

$ErrorActionPreference = "Stop"

Write-Host "IntentHub model policy JDBC smoke"

$root = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $root "intent-hub-interfaces/target/intent-hub-interfaces-0.1.0-SNAPSHOT.jar"
$logDir = Join-Path $root "target/smoke"
$stdoutLog = Join-Path $logDir "intent-hub-model-policy-jdbc.out.log"
$stderrLog = Join-Path $logDir "intent-hub-model-policy-jdbc.err.log"
$pgContainer = "intent-hub-model-policy-postgres"
$appProcess = $null

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

function Get-JavaVersionText {
    param([string] $Path)
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = & $Path -version 2>&1
        return $output -join "`n"
    } finally {
        $ErrorActionPreference = $previousPreference
    }
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

function Convert-ModelPolicy {
    param([object] $Value)
    if ($null -eq $Value) {
        return $null
    }
    if ($Value -is [string]) {
        return $Value | ConvertFrom-Json
    }
    if ($Value.PSObject.Properties.Name -contains "value") {
        return $Value.value | ConvertFrom-Json
    }
    return $Value
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

function Stop-App {
    if ($script:appProcess -and -not $script:appProcess.HasExited) {
        Stop-Process -Id $script:appProcess.Id -Force -ErrorAction SilentlyContinue
        Ok "Intent Hub process stopped"
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

try {
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    Assert-Port-Free 8080
    Assert-Port-Free $PostgresPort

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
    $javaVersionText = Get-JavaVersionText -Path $java
    if ($javaVersionText -notmatch 'version "1[7-9]\.' -and $javaVersionText -notmatch 'version "2[0-9]\.') {
        Fail "Java 17+ is required; actual output: $javaVersionText"
    }
    Use-JavaForChildProcesses -Path $java
    Ok "Java 17+ detected: $java"

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

    $arguments = @(
        "-jar", $jarPath,
        "--spring.profiles.active=local-jdbc",
        "--spring.datasource.url=jdbc:postgresql://localhost:$PostgresPort/intent_hub",
        "--intent-hub.model-service.enabled=true",
        "--intent-hub.model-service.base-url=http://127.0.0.1:65530",
        "--intent-hub.model-service.timeout-ms=500"
    )
    $script:appProcess = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog -PassThru
    Ok "Intent Hub process started: pid=$($script:appProcess.Id)"

    $health = Wait-HttpJson -Uri "http://localhost:8080/api/v1/admin/health" -TimeoutSeconds $StartupTimeoutSeconds
    if ($health.status -ne "UP") {
        Fail "Intent Hub health is not UP"
    }
    Ok "Intent Hub health is UP"

    $flywayVersions = Invoke-Postgres "select string_agg(version, ',' order by installed_rank) from flyway_schema_history where success = true;"
    if ($flywayVersions -ne "1,2,3") {
        Fail "Flyway versions are not 1,2,3; actual: $flywayVersions"
    }
    Ok "Flyway applied V1/V2/V3"

    $modelPolicyColumn = Invoke-Postgres "select count(*) from information_schema.columns where table_name = 'nlu_strategy' and column_name = 'model_policy';"
    if ($modelPolicyColumn -ne "1") {
        Fail "nlu_strategy.model_policy column was not created"
    }
    Ok "nlu_strategy.model_policy column exists"

    $tenantId = "tenant-model-policy-smoke"
    $sceneId = "model-policy-smoke-scene"
    $version = "v-model-policy-smoke"

    Invoke-IntentHubJson -Method Post -Uri "http://localhost:8080/api/v1/admin/config/versions" -Body @{
        tenantId = $tenantId
        sceneId = $sceneId
        version = $version
        description = "model policy jdbc smoke"
        actor = "model-policy-smoke"
    } | Out-Null

    Invoke-IntentHubJson -Method Post -Uri "http://localhost:8080/api/v1/admin/config/versions/$version/intents?tenantId=$tenantId&sceneId=$sceneId" -Body @{
        actor = "model-policy-smoke"
        payload = @{
            intentCode = "SMOKE_MODEL_ONLY"
            intentName = "Model only smoke"
            enabled = $true
            definition = @{
                matchType = "CONTAINS"
                pattern = "never-match-by-rule"
                confidence = 0.91
                explanation = "rule should not match"
            }
        }
    } | Out-Null

    Invoke-IntentHubJson -Method Post -Uri "http://localhost:8080/api/v1/admin/config/versions/$version/strategies?tenantId=$tenantId&sceneId=$sceneId" -Body @{
        actor = "model-policy-smoke"
        payload = @{
            strategyCode = "default"
            confidenceThreshold = 0.60
            llmPolicy = @{}
            modelPolicy = @{
                enabled = $false
                endpoint = "https://model.example.test"
                timeoutMs = 1800
                minConfidence = 0.72
            }
        }
    } | Out-Null

    $strategyList = Invoke-IntentHubJson -Method Get -Uri "http://localhost:8080/api/v1/admin/config/versions/$version/strategies?tenantId=$tenantId&sceneId=$sceneId"
    $strategy = @($strategyList)[0]
    $listedPolicy = Convert-ModelPolicy $strategy.model_policy
    if ($null -eq $listedPolicy) {
        $listedPolicy = Convert-ModelPolicy $strategy.modelPolicy
    }
    if ($null -eq $listedPolicy -or $listedPolicy.enabled -ne $false) {
        $actual = $strategy | ConvertTo-Json -Depth 10 -Compress
        Fail "Admin list did not expose model_policy.enabled=false; actual: $actual"
    }
    Ok "Admin list exposes model_policy"

    $storedPolicy = Invoke-Postgres "select model_policy::text from nlu_strategy where tenant_id = '$tenantId' and scene_id = '$sceneId' and version = '$version' and strategy_code = 'default';"
    if ($storedPolicy -notmatch '"enabled": false' -or $storedPolicy -notmatch '"minConfidence": 0.72') {
        Fail "stored model_policy is unexpected: $storedPolicy"
    }
    Ok "model_policy JSON persisted"

    $validation = Invoke-IntentHubJson -Method Post -Uri "http://localhost:8080/api/v1/admin/config/versions/$version/validate?tenantId=$tenantId&sceneId=$sceneId"
    if (-not $validation.valid) {
        Fail "config validation failed: $($validation.errors -join '; ')"
    }
    Invoke-IntentHubJson -Method Post -Uri "http://localhost:8080/api/v1/admin/config/versions/$version/publish?tenantId=$tenantId&sceneId=$sceneId" -Body @{
        actor = "model-policy-smoke"
    } | Out-Null
    Ok "config published"

    $recognizeBody = @{
        tenantId = $tenantId
        source = "smoke"
        channel = "cli"
        inputType = "TEXT"
        requestId = "req-model-policy-jdbc-smoke"
        traceId = "trace-model-policy-jdbc-smoke"
        text = "cancel A100"
        metadata = @{
            scene_id = $sceneId
        }
        attachments = @()
    }
    $result = Invoke-IntentHubJson -Method Post -Uri "http://localhost:8080/api/v1/intent/recognize" -Body $recognizeBody
    if ($result.decision -ne "REJECTED" -or $result.intentCode -ne "UNKNOWN") {
        Fail "recognition was not rejected by model policy; decision=$($result.decision), intent=$($result.intentCode)"
    }
    if ($result.recognitionPath -notcontains "MODEL_POLICY:DISABLED") {
        Fail "recognition path does not contain MODEL_POLICY:DISABLED"
    }
    if ($result.recognitionPath -contains "MODEL_FALLBACK:CLOSED") {
        Fail "model adapter was called even though model policy is disabled"
    }
    Ok "published modelPolicy disables model recognition"

    Write-Host "[OK] model policy JDBC smoke completed"
} finally {
    Stop-App
    Stop-Postgres
}
