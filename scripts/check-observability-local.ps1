param(
    [string] $BaseUrl = "http://localhost:8080",
    [switch] $RequireDocker
)

Write-Host "IntentHub local observability preflight"
Write-Host "[INFO] BaseUrl: $BaseUrl"

$root = Split-Path -Parent $PSScriptRoot
$requiredFiles = @(
    "ops/local-observability/docker-compose.yml",
    "ops/local-observability/prometheus.yml",
    "ops/local-observability/alertmanager.yml",
    "ops/local-observability/grafana/provisioning/datasources/prometheus.yml",
    "ops/local-observability/grafana/provisioning/dashboards/intent-hub.yml",
    "ops/prometheus/intent-hub-alert-rules.yml",
    "ops/grafana/intent-hub-dashboard.json"
)

$failed = $false

foreach ($file in $requiredFiles) {
    $path = Join-Path $root $file
    if (Test-Path $path) {
        Write-Host "[OK] file exists: $file"
    } else {
        Write-Host "[FAIL] missing file: $file"
        $failed = $true
    }
}

$healthUrl = "$BaseUrl/api/v1/admin/health"
$metricsUrl = "$BaseUrl/api/v1/admin/metrics/prometheus"

try {
    $health = Invoke-WebRequest -Uri $healthUrl -UseBasicParsing -TimeoutSec 5
    if ($health.StatusCode -eq 200 -and $health.Content -match "UP") {
        Write-Host "[OK] health endpoint is UP"
    } else {
        Write-Host "[WARN] health endpoint responded but did not clearly report UP"
    }
} catch {
    Write-Host "[WARN] health endpoint unavailable: $healthUrl"
    Write-Host "       Start Intent Hub first, then rerun this script."
}

try {
    $metrics = Invoke-WebRequest -Uri $metricsUrl -UseBasicParsing -TimeoutSec 5
    if ($metrics.StatusCode -eq 200 -and $metrics.Content -match "intent_hub_requests_total") {
        Write-Host "[OK] Prometheus metrics endpoint is available"
    } else {
        Write-Host "[WARN] metrics endpoint responded but expected metric was not found"
    }
} catch {
    Write-Host "[WARN] metrics endpoint unavailable: $metricsUrl"
    Write-Host "       Prometheus target will stay DOWN until Intent Hub exposes metrics."
}

$docker = Get-Command docker -ErrorAction SilentlyContinue
if ($docker) {
    Write-Host "[OK] docker command found"
    try {
        $dockerVersion = & docker --version
        Write-Host "[INFO] $dockerVersion"
    } catch {
        Write-Host "[WARN] docker command exists but version check failed"
    }
} elseif ($RequireDocker) {
    Write-Host "[FAIL] docker command not found"
    $failed = $true
} else {
    Write-Host "[WARN] docker command not found; local observability stack cannot be started here"
}

$composeFile = Join-Path $root "ops/local-observability/docker-compose.yml"
if (Test-Path $composeFile) {
    Write-Host "[INFO] Next command:"
    Write-Host "       cd ops/local-observability"
    Write-Host "       docker compose up -d"
}

if ($failed) {
    Write-Host "[FAIL] local observability preflight failed"
    exit 1
}

Write-Host "[OK] local observability preflight completed"
