param(
    [string] $JavaExe,
    [string] $MavenExe,
    [switch] $SkipPackage,
    [switch] $KeepRunning,
    [int] $StartupTimeoutSeconds = 60,
    [int] $ModelTimeoutMs = 2000,
    [string] $ModelAuthTokenRef = "INTENT_HUB_MODEL_ROTATION_TOKEN",
    [string] $InitialToken = "intent-hub-model-token-v1",
    [string] $RotatedToken = "intent-hub-model-token-v2",
    [int] $PostgresPort = 15434
)

$ErrorActionPreference = "Stop"

Write-Host "IntentHub secret rotation smoke"

$root = Split-Path -Parent $PSScriptRoot
$exampleDir = Join-Path $root "examples/model-service-fastapi"
$jarPath = Join-Path $root "intent-hub-interfaces/target/intent-hub-interfaces-0.1.0-SNAPSHOT.jar"
$logDir = Join-Path $root "target/smoke"
$stdoutLog = Join-Path $logDir "intent-hub-secret-rotation.out.log"
$stderrLog = Join-Path $logDir "intent-hub-secret-rotation.err.log"
$secretRoot = Join-Path $logDir "secret-rotation"
$secretFile = Join-Path $secretRoot $ModelAuthTokenRef
$pgContainer = "intent-hub-secret-rotation-postgres"
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

function Assert-Port-Free {
    param([int] $Port)
    $connection = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue
    if ($connection) {
        Fail "local port $Port is already in use"
    }
}

function Use-JavaForChildProcesses {
    param([string] $Path)
    $javaBin = Split-Path -Parent $Path
    $javaHome = Split-Path -Parent $javaBin
    $env:JAVA_HOME = $javaHome
    $env:PATH = "$javaBin;$env:PATH"
}

function Stop-App {
    if ($script:appProcess -and -not $script:appProcess.HasExited) {
        Stop-Process -Id $script:appProcess.Id -Force -ErrorAction SilentlyContinue
        Ok "Intent Hub process stopped"
    }
}

function Stop-ModelService {
    if (-not $KeepRunning) {
        Push-Location $exampleDir
        try {
            & docker compose down | Out-Host
            Ok "model service container stopped"
        } finally {
            Pop-Location
        }
    } else {
        Write-Host "[WARN] KeepRunning is enabled; model service container is still running"
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

function Invoke-DirectModel {
    param([string] $Token)
    $headers = @{ Authorization = "Bearer $Token" }
    $body = @{ text = "cancel A100"; sceneId = "order-scene" } | ConvertTo-Json -Compress
    return Invoke-RestMethod -Method Post -Uri "http://localhost:18081/recognize" -Headers $headers -ContentType "application/json" -Body $body -TimeoutSec 5
}

function Assert-DirectModelUnauthorized {
    param([string] $Token)
    $headers = @{ Authorization = "Bearer $Token" }
    $body = @{ text = "cancel A100"; sceneId = "order-scene" } | ConvertTo-Json -Compress
    $unauthorized = $false
    try {
        Invoke-RestMethod -Method Post -Uri "http://localhost:18081/recognize" -Headers $headers -ContentType "application/json" -Body $body -TimeoutSec 5 | Out-Null
    } catch {
        $unauthorized = ($_.Exception.Response.StatusCode.value__ -eq 401)
    }
    if (-not $unauthorized) {
        Fail "direct model recognition with old token was not rejected"
    }
}

function Invoke-Recognition {
    param(
        [string] $TenantId,
        [string] $SceneId,
        [string] $RequestId,
        [string] $TraceId
    )
    $body = @{
        tenantId = $TenantId
        source = "web"
        channel = "secret-rotation-smoke"
        inputType = "TEXT"
        requestId = $RequestId
        traceId = $TraceId
        text = "cancel A100"
        metadata = @{ scene_id = $SceneId }
    } | ConvertTo-Json -Depth 10 -Compress
    return Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/intent/recognize" -ContentType "application/json" -Body $body -TimeoutSec 10
}

try {
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    if (Test-Path $secretRoot) {
        Remove-Item -LiteralPath $secretRoot -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $secretRoot | Out-Null
    Set-Content -Path $secretFile -Value $InitialToken -NoNewline

    Assert-Port-Free 8080
    Assert-Port-Free 18081
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

    Push-Location $exampleDir
    try {
        Remove-Item Env:MODEL_SERVICE_AUTH_TOKEN -ErrorAction SilentlyContinue
        $env:MODEL_SERVICE_SECRET_DIR = $secretRoot
        $env:MODEL_SERVICE_AUTH_TOKEN_FILE = "/run/secrets/intent-hub-model/$ModelAuthTokenRef"
        & docker compose up --build -d | Out-Host
        if ($LASTEXITCODE -ne 0) {
            Fail "docker compose up exited with code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
    Ok "model service container started with file-mounted token"

    $modelHealth = Wait-HttpJson -Uri "http://localhost:18081/health" -TimeoutSeconds $StartupTimeoutSeconds
    if ($modelHealth.status -ne "UP") {
        Fail "model service health is not UP"
    }
    Ok "model service health is UP"

    Invoke-DirectModel -Token $InitialToken | Out-Null
    Ok "direct model recognition accepted initial token"

    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root "scripts/preflight-external-integration.ps1") `
        -SkipIntentHub `
        -ModelServiceBaseUrl "http://localhost:18081" `
        -ModelAuthTokenRef $ModelAuthTokenRef `
        -RequireModelAuth `
        -SecretFileRoot $secretRoot `
        -SkipDashScope | Out-Host
    if ($LASTEXITCODE -ne 0) {
        Fail "external integration preflight failed"
    }
    Ok "external integration preflight passed with file-mounted model auth"

    $arguments = @(
        "-jar", $jarPath,
        "--spring.profiles.active=local-jdbc",
        "--spring.datasource.url=jdbc:postgresql://localhost:$PostgresPort/intent_hub",
        "--intent-hub.model-service.enabled=true",
        "--intent-hub.model-service.base-url=http://localhost:18081",
        "--intent-hub.model-service.timeout-ms=$ModelTimeoutMs",
        "--intent-hub.secret.file-root=$secretRoot"
    )
    $script:appProcess = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog -PassThru
    Ok "Intent Hub process started: pid=$($script:appProcess.Id)"

    $hubHealth = Wait-HttpJson -Uri "http://localhost:8080/api/v1/admin/health" -TimeoutSeconds $StartupTimeoutSeconds
    if ($hubHealth.status -ne "UP") {
        Fail "Intent Hub health is not UP"
    }
    Ok "Intent Hub health is UP"

    $tenantId = "tenant-secret-rotation-smoke"
    $sceneId = "secret-rotation-smoke-scene"
    $version = "v-secret-rotation-smoke"

    Invoke-IntentHubJson -Method Post -Uri "http://localhost:8080/api/v1/admin/config/versions" -Body @{
        tenantId = $tenantId
        sceneId = $sceneId
        version = $version
        description = "secret rotation smoke"
        actor = "secret-rotation-smoke"
    } | Out-Null

    Invoke-IntentHubJson -Method Post -Uri "http://localhost:8080/api/v1/admin/config/versions/$version/intents?tenantId=$tenantId&sceneId=$sceneId" -Body @{
        actor = "secret-rotation-smoke"
        payload = @{
            intentCode = "ORDER_CANCEL"
            intentName = "Secret rotation order cancel smoke"
            enabled = $true
            definition = @{
                matchType = "CONTAINS"
                pattern = "never-match-by-rule"
                confidence = 0.91
                explanation = "rule should not match"
            }
        }
    } | Out-Null

    Invoke-IntentHubJson -Method Post -Uri "http://localhost:8080/api/v1/admin/config/versions/$version/downstream-actions?tenantId=$tenantId&sceneId=$sceneId" -Body @{
        actor = "secret-rotation-smoke"
        payload = @{
            actionCode = "ORDER_CANCEL_COMMAND"
            actionType = "MQ"
            target = "order.cancel"
            idempotencyRequired = $true
            timeoutMs = 1500
            actionSchema = @{ intentCode = "ORDER_CANCEL" }
        }
    } | Out-Null

    Invoke-IntentHubJson -Method Post -Uri "http://localhost:8080/api/v1/admin/config/versions/$version/strategies?tenantId=$tenantId&sceneId=$sceneId" -Body @{
        actor = "secret-rotation-smoke"
        payload = @{
            strategyCode = "default"
            confidenceThreshold = 0.60
            llmPolicy = @{}
            modelPolicy = @{
                enabled = $true
                endpoint = "http://localhost:18081"
                timeoutMs = $ModelTimeoutMs
                minConfidence = 0.70
                authTokenRef = $ModelAuthTokenRef
            }
        }
    } | Out-Null

    Invoke-IntentHubJson -Method Post -Uri "http://localhost:8080/api/v1/admin/config/versions/$version/validate?tenantId=$tenantId&sceneId=$sceneId" | Out-Null
    Invoke-IntentHubJson -Method Post -Uri "http://localhost:8080/api/v1/admin/config/versions/$version/publish?tenantId=$tenantId&sceneId=$sceneId" -Body @{
        actor = "secret-rotation-smoke"
    } | Out-Null
    Ok "secret rotation smoke config published"

    $firstResult = Invoke-Recognition -TenantId $tenantId -SceneId $sceneId -RequestId "req-secret-rotation-1" -TraceId "trace-secret-rotation-1"
    if ($firstResult.intentCode -ne "ORDER_CANCEL" -or $firstResult.recognitionPath -notcontains "ModelRecognitionPolicy") {
        Fail "first recognition did not pass through model policy"
    }
    Ok "first Intent Hub recognition succeeded with initial token"

    Set-Content -Path $secretFile -Value $RotatedToken -NoNewline
    Start-Sleep -Seconds 1
    Assert-DirectModelUnauthorized -Token $InitialToken
    Ok "direct model recognition rejects old token after rotation"
    Invoke-DirectModel -Token $RotatedToken | Out-Null
    Ok "direct model recognition accepts rotated token"

    $secondResult = Invoke-Recognition -TenantId $tenantId -SceneId $sceneId -RequestId "req-secret-rotation-2" -TraceId "trace-secret-rotation-2"
    if ($secondResult.intentCode -ne "ORDER_CANCEL" -or $secondResult.recognitionPath -notcontains "ModelRecognitionPolicy") {
        Fail "second recognition did not pass through model policy after token rotation"
    }
    Ok "second Intent Hub recognition succeeded after token rotation"

    Write-Host "[OK] secret rotation smoke completed"
} finally {
    Stop-App
    Stop-ModelService
    Stop-Postgres
    if (-not $KeepRunning -and (Test-Path $secretRoot)) {
        Remove-Item -LiteralPath $secretRoot -Recurse -Force -ErrorAction SilentlyContinue
        Ok "secret rotation files removed"
    }
}
