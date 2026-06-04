param(
    [switch] $RequireDocker
)

Write-Host "IntentHub local observability compose validation"

$root = Split-Path -Parent $PSScriptRoot
$composeDir = Join-Path $root "ops/local-observability"
$composeFile = Join-Path $composeDir "docker-compose.yml"
$prometheusFile = Join-Path $composeDir "prometheus.yml"
$alertmanagerFile = Join-Path $composeDir "alertmanager.yml"
$grafanaDatasource = Join-Path $composeDir "grafana/provisioning/datasources/prometheus.yml"
$grafanaDashboardProvisioning = Join-Path $composeDir "grafana/provisioning/dashboards/intent-hub.yml"
$alertRules = Join-Path $root "ops/prometheus/intent-hub-alert-rules.yml"
$grafanaDashboard = Join-Path $root "ops/grafana/intent-hub-dashboard.json"

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

Assert-File $composeFile "docker-compose.yml"
Assert-File $prometheusFile "prometheus.yml"
Assert-File $alertmanagerFile "alertmanager.yml"
Assert-File $grafanaDatasource "grafana datasource provisioning"
Assert-File $grafanaDashboardProvisioning "grafana dashboard provisioning"
Assert-File $alertRules "Prometheus alert rules"
Assert-File $grafanaDashboard "Grafana dashboard json"

if (Test-Path $composeFile) {
    $composeText = Get-Content -Raw -Encoding UTF8 $composeFile
    $expectedRefs = @(
        "./prometheus.yml:/etc/prometheus/prometheus.yml:ro",
        "../prometheus/intent-hub-alert-rules.yml:/etc/prometheus/rules/intent-hub-alert-rules.yml:ro",
        "./alertmanager.yml:/etc/alertmanager/alertmanager.yml:ro",
        "./grafana/provisioning:/etc/grafana/provisioning:ro",
        "../grafana/intent-hub-dashboard.json:/var/lib/grafana/dashboards/intent-hub-dashboard.json:ro"
    )
    foreach ($ref in $expectedRefs) {
        if ($composeText.Contains($ref)) {
            Write-Host "[OK] compose references $ref"
        } else {
            Write-Host "[FAIL] compose missing reference $ref"
            $failed = $true
        }
    }
}

if (Test-Path $prometheusFile) {
    $prometheusText = Get-Content -Raw -Encoding UTF8 $prometheusFile
    if ($prometheusText -match "/etc/prometheus/rules/intent-hub-alert-rules.yml") {
        Write-Host "[OK] Prometheus rule file reference"
    } else {
        Write-Host "[FAIL] Prometheus rule file reference missing"
        $failed = $true
    }
    if ($prometheusText -match "host\.docker\.internal:8080") {
        Write-Host "[OK] Prometheus target points to host.docker.internal:8080"
    } else {
        Write-Host "[FAIL] Prometheus target host.docker.internal:8080 missing"
        $failed = $true
    }
    if ($prometheusText -match "/api/v1/admin/metrics/prometheus") {
        Write-Host "[OK] Prometheus metrics path configured"
    } else {
        Write-Host "[FAIL] Prometheus metrics path missing"
        $failed = $true
    }
}

$docker = Get-Command docker -ErrorAction SilentlyContinue
if ($docker) {
    Write-Host "[OK] docker command found"
    Push-Location $composeDir
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
    Write-Host "[FAIL] local observability compose validation failed"
    exit 1
}

Write-Host "[OK] local observability compose validation completed"
