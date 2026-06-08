param(
    [string] $JavaExe,
    [string] $MavenExe,
    [switch] $SkipPackage,
    [switch] $KeepRunning,
    [int] $StartupTimeoutSeconds = 60,
    [int] $ModelTimeoutMs = 2000
)

$ErrorActionPreference = "Stop"

Write-Host "IntentHub model service end-to-end smoke"

$root = Split-Path -Parent $PSScriptRoot
$exampleDir = Join-Path $root "examples/model-service-fastapi"
$jarPath = Join-Path $root "intent-hub-interfaces/target/intent-hub-interfaces-0.1.0-SNAPSHOT.jar"
$logDir = Join-Path $root "target/smoke"
$stdoutLog = Join-Path $logDir "intent-hub-model-service-e2e.out.log"
$stderrLog = Join-Path $logDir "intent-hub-model-service-e2e.err.log"
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

function Assert-Port-Free {
    param([int] $Port)
    $connection = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue
    if ($connection) {
        Fail "local port $Port is already in use"
    }
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

try {
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    Assert-Port-Free 8080
    Assert-Port-Free 18081

    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $docker) {
        Fail "docker command not found"
    }
    Ok "docker command found"

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

    Push-Location $exampleDir
    try {
        & docker compose up --build -d | Out-Host
        if ($LASTEXITCODE -ne 0) {
            Fail "docker compose up exited with code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
    Ok "model service container started"

    $modelHealth = Wait-HttpJson -Uri "http://localhost:18081/health" -TimeoutSeconds $StartupTimeoutSeconds
    if ($modelHealth.status -ne "UP") {
        Fail "model service health is not UP"
    }
    if ($modelHealth.modelVersion -ne "fastapi-example-2026-06-08") {
        Fail "model service version is not fastapi-example-2026-06-08"
    }
    Ok "model service health is UP"

    $directBody = @{ text = "cancel A100"; tenant_id = "tenant-a"; scene_id = "order-scene"; request_id = "direct-model-e2e" } | ConvertTo-Json -Compress
    $directResult = Invoke-RestMethod -Method Post -Uri "http://localhost:18081/recognize" -ContentType "application/json" -Body $directBody -TimeoutSec 5
    if ($directResult.intentCode -ne "ORDER_CANCEL" -or $directResult.slots.order_id -ne "A100") {
        Fail "direct model recognition did not return ORDER_CANCEL/A100"
    }
    if ($directResult.modelVersion -ne "fastapi-example-2026-06-08") {
        Fail "direct model recognition did not return expected modelVersion"
    }
    Ok "direct model recognition returned ORDER_CANCEL/A100"

    $arguments = @(
        "-jar", $jarPath,
        "--intent-hub.model-service.enabled=true",
        "--intent-hub.model-service.base-url=http://localhost:18081",
        "--intent-hub.model-service.timeout-ms=$ModelTimeoutMs"
    )
    $script:appProcess = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog -PassThru
    Ok "Intent Hub process started: pid=$($script:appProcess.Id)"

    $hubHealth = Wait-HttpJson -Uri "http://localhost:8080/api/v1/admin/health" -TimeoutSeconds $StartupTimeoutSeconds
    if ($hubHealth.status -ne "UP" -or -not $hubHealth.model_service.healthy) {
        Fail "Intent Hub health did not report model_service.healthy=true"
    }
    if ($hubHealth.model_service.modelVersion -ne "fastapi-example-2026-06-08") {
        Fail "Intent Hub health did not expose model service version"
    }
    Ok "Intent Hub health reports model_service.healthy=true"

    $recognizeBody = @{
        tenantId = "tenant-a"
        source = "web"
        channel = "smoke"
        inputType = "TEXT"
        requestId = "req-model-e2e-smoke"
        traceId = "trace-model-e2e-smoke"
        text = "cancel A100"
        metadata = @{ scene_id = "order-scene" }
    } | ConvertTo-Json -Depth 10 -Compress
    $recognizeResult = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/intent/recognize" -ContentType "application/json" -Body $recognizeBody -TimeoutSec 10
    if ($recognizeResult.intentCode -ne "ORDER_CANCEL" -or $recognizeResult.decision -ne "ASYNC_ACCEPTED") {
        Fail "Intent Hub recognition did not return ORDER_CANCEL/ASYNC_ACCEPTED"
    }
    if ($recognizeResult.recognitionPath -notcontains "ModelRecognitionPolicy") {
        Fail "Intent Hub recognition path did not include ModelRecognitionPolicy"
    }
    Ok "Intent Hub recognition returned ORDER_CANCEL/ASYNC_ACCEPTED through ModelRecognitionPolicy"

    Write-Host "[OK] end-to-end smoke completed"
} finally {
    Stop-App
    Stop-ModelService
}
