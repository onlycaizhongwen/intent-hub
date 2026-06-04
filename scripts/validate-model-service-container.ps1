param(
    [switch] $RequireDocker
)

Write-Host "IntentHub model service container validation"

$root = Split-Path -Parent $PSScriptRoot
$exampleDir = Join-Path $root "examples/model-service-fastapi"
$dockerfile = Join-Path $exampleDir "Dockerfile"
$composeFile = Join-Path $exampleDir "docker-compose.yml"
$requirementsFile = Join-Path $exampleDir "requirements.txt"
$appFile = Join-Path $exampleDir "app.py"

$failed = $false

function Assert-File {
    param([string] $Path, [string] $Label)
    if (Test-Path $Path) {
        Write-Host "[OK] $Label"
    } else {
        Write-Host "[FAIL] missing $Label"
        $script:failed = $true
    }
}

function Assert-Contains {
    param([string] $Text, [string] $Pattern, [string] $Label)
    if ($Text -match $Pattern) {
        Write-Host "[OK] $Label"
    } else {
        Write-Host "[FAIL] $Label"
        $script:failed = $true
    }
}

Assert-File $dockerfile "Dockerfile"
Assert-File $composeFile "docker-compose.yml"
Assert-File $requirementsFile "requirements.txt"
Assert-File $appFile "app.py"

if (Test-Path $dockerfile) {
    $dockerfileText = Get-Content -Raw -Encoding UTF8 $dockerfile
    Assert-Contains $dockerfileText "FROM python:3\.12-slim" "Dockerfile uses python:3.12-slim"
    Assert-Contains $dockerfileText "pip install --no-cache-dir -r requirements\.txt" "Dockerfile installs requirements"
    Assert-Contains $dockerfileText "EXPOSE 18081" "Dockerfile exposes model service port"
    Assert-Contains $dockerfileText "uvicorn" "Dockerfile starts uvicorn"
}

if (Test-Path $composeFile) {
    $composeText = Get-Content -Raw -Encoding UTF8 $composeFile
    Assert-Contains $composeText "intent-hub-model-service" "compose service name"
    Assert-Contains $composeText "18081:18081" "compose publishes model service port"
    Assert-Contains $composeText "healthcheck:" "compose healthcheck configured"
    Assert-Contains $composeText "http://127\.0\.0\.1:18081/health" "compose healthcheck targets /health"
}

$docker = Get-Command docker -ErrorAction SilentlyContinue
if ($docker) {
    Write-Host "[OK] docker command found"
    Push-Location $exampleDir
    try {
        & docker compose config | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "[OK] docker compose config"
        } else {
            Write-Host "[FAIL] docker compose config exited with code $LASTEXITCODE"
            $failed = $true
        }
    } finally {
        Pop-Location
    }
} elseif ($RequireDocker) {
    Write-Host "[FAIL] docker command not found"
    $failed = $true
} else {
    Write-Host "[WARN] docker command not found; skipped docker compose config"
}

if ($failed) {
    Write-Host "[FAIL] model service container validation failed"
    exit 1
}

Write-Host "[OK] model service container validation completed"

