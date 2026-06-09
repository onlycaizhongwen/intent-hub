param(
    [string] $JavaExe,
    [string] $MavenExe,
    [switch] $SkipPackage,
    [switch] $KeepRunning,
    [int] $StartupTimeoutSeconds = 90,
    [int] $Port = 18091,
    [string] $RecordPath = "ops/pilot-execution-record-local.md"
)

$ErrorActionPreference = "Stop"

Write-Host "IntentHub local observability pilot smoke"

$root = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $root "intent-hub-interfaces/target/intent-hub-interfaces-0.1.0-SNAPSHOT.jar"
$logDir = Join-Path $root "target/smoke"
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

function Use-JavaForChildProcesses {
    param([string] $Path)
    $javaBin = Split-Path -Parent $Path
    $javaHome = Split-Path -Parent $javaBin
    $env:JAVA_HOME = $javaHome
    $env:PATH = "$javaBin;$env:PATH"
}

function Assert-Port-Free {
    param([int] $LocalPort)
    $connection = Get-NetTCPConnection -LocalPort $LocalPort -ErrorAction SilentlyContinue
    if ($connection) {
        Fail "local port $LocalPort is already in use"
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

    if ($null -eq $Body) {
        return Invoke-RestMethod -Method $Method -Uri $Uri -TimeoutSec 10
    }
    $json = $Body | ConvertTo-Json -Depth 20 -Compress
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    return Invoke-RestMethod -Method $Method -Uri $Uri -ContentType "application/json; charset=utf-8" -Body $bytes -TimeoutSec 10
}

function Start-IntentHub {
    param(
        [int] $LocalPort,
        [string] $Java
    )
    $stdoutLog = Join-Path $logDir "intent-hub-observability-pilot.out.log"
    $stderrLog = Join-Path $logDir "intent-hub-observability-pilot.err.log"
    $arguments = @(
        "-jar", $jarPath,
        "--server.port=$LocalPort",
        "--intent-hub.model-service.enabled=false",
        "--intent-hub.llm.enabled=false"
    )
    $process = Start-Process -FilePath $Java -ArgumentList $arguments -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog -PassThru
    Ok "Intent Hub started: port=$LocalPort pid=$($process.Id)"
    return $process
}

function Stop-ProcessIfRunning {
    param([System.Diagnostics.Process] $Process, [string] $Name)
    if ($Process -and -not $Process.HasExited) {
        Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue
        Ok "$Name stopped"
    }
}

function Invoke-Recognition {
    param(
        [string] $BaseUrl,
        [int] $Index,
        [bool] $Reject
    )
    $orderQueryText = "$([char]0x67E5)$([char]0x4E00)$([char]0x4E0B)$([char]0x8BA2)$([char]0x5355) $Index"
    $body = @{
        tenantId = "tenant-observability-local"
        source = "pilot-smoke"
        channel = "local-observability"
        inputType = "TEXT"
        requestId = "req-observability-local-$Index"
        traceId = "trace-observability-local-$Index"
        text = if ($Reject) { "unmatched local observability sample $Index" } else { $orderQueryText }
        metadata = @{ scene_id = "order-scene" }
        attachments = @()
    }
    return Invoke-IntentHubJson -Method Post -Uri "$BaseUrl/api/v1/intent/recognize" -Body $body
}

function Write-Record {
    param(
        [string] $Path,
        [string] $BaseUrl,
        [object] $Metrics,
        [string] $Prometheus,
        [object] $Alerts,
        [int] $Successes,
        [int] $Rejected
    )
    $absolute = Join-Path $root $Path
    $dir = Split-Path -Parent $absolute
    New-Item -ItemType Directory -Force -Path $dir | Out-Null

    $generatedAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss zzz")
    $alertCodes = if ($Alerts.alerts) { ($Alerts.alerts | ForEach-Object { $_.code }) -join ", " } else { "none" }
    $prometheusHasRequests = $Prometheus -match "intent_hub_requests_total"
    $prometheusHasBadCases = $Prometheus -match "intent_hub_bad_cases_total"
    $requestsMetricResult = if ($prometheusHasRequests) { "PASS" } else { "FAIL" }
    $badCasesMetricResult = if ($prometheusHasBadCases) { "PASS" } else { "FAIL" }
    $jsonRequestsResult = if ($Metrics.totalRequests -eq 10) { "PASS" } else { "FAIL" }
    $jsonBadCasesResult = if ($Metrics.totalBadCases -eq 4) { "PASS" } else { "FAIL" }
    $badCaseAlertResult = if ($alertCodes -match "BAD_CASE_RATE_HIGH") { "PASS" } else { "FAIL" }

    $content = @"
# Intent Hub Local Observability Pilot Execution Record

Generated by ``scripts/smoke-observability-pilot-local.ps1`` for repeatable P2-8 local evidence.
This record is not a real dev/staging/production Prometheus, Alertmanager, or Grafana integration result.

## Basic Info

| Item | Value |
| --- | --- |
| Pilot environment | local |
| Tenant | tenant-observability-local |
| Scene | order-scene |
| Intent Hub version | local working tree |
| Instance URL | $BaseUrl |
| Prometheus | not attached; endpoint text verified only |
| Alertmanager | not attached |
| Grafana | not attached |
| Execution window | $generatedAt |
| Executor | Codex |

## Local Traffic

| Metric | Value |
| --- | --- |
| total requests | $($Metrics.totalRequests) |
| successes | $Successes |
| rejected | $Rejected |
| bad cases | $($Metrics.totalBadCases) |
| avg latency ms | $($Metrics.averageLatencyMillis) |
| p95 latency ms | $($Metrics.p95LatencyMillis) |
| p99 latency ms | $($Metrics.p99LatencyMillis) |

## Endpoint Checks

| Check | Result |
| --- | --- |
| JSON totalRequests equals 10 | $jsonRequestsResult |
| JSON totalBadCases equals 4 | $jsonBadCasesResult |
| Prometheus has intent_hub_requests_total | $requestsMetricResult |
| Prometheus has intent_hub_bad_cases_total | $badCasesMetricResult |

## Alerts Snapshot

| Item | Value |
| --- | --- |
| alert status | $($Alerts.status) |
| alert codes | $alertCodes |
| BAD_CASE_RATE_HIGH triggered | $badCaseAlertResult |

## Conclusion

| Item | Conclusion |
| --- | --- |
| Local pilot passed | yes |
| Next environment | dev/staging integration preparation |
| Required next inputs | Prometheus, Alertmanager, Grafana URLs and sandbox receiver |
| Blocking gap | real Prometheus target, Alertmanager route, and Grafana dashboard are not integrated |
| Follow-up owner | TBD |

## Notes

- No receiver secret, token, or signing key is included in this record.
- Raw trace input and bad case text are not pasted here.
- This record proves only local endpoint and alert snapshot availability, not real operations-stack integration.
"@
    Set-Content -Path $absolute -Encoding UTF8 -Value $content
}

try {
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    Assert-Port-Free -LocalPort $Port

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

    $script:appProcess = Start-IntentHub -LocalPort $Port -Java $java
    $baseUrl = "http://localhost:$Port"
    $health = Wait-HttpJson -Uri "$baseUrl/api/v1/admin/health" -TimeoutSeconds $StartupTimeoutSeconds
    if ($health.status -ne "UP") {
        Fail "Intent Hub health is not UP"
    }
    Ok "Intent Hub health is UP"

    $successes = 0
    $rejected = 0
    for ($i = 1; $i -le 10; $i++) {
        $reject = $i -le 4
        $result = Invoke-Recognition -BaseUrl $baseUrl -Index $i -Reject $reject
        if ($result.decision -eq "SUCCESS") {
            $successes++
        } elseif ($result.decision -eq "REJECTED") {
            $rejected++
        } else {
            Fail "unexpected decision for request $i`: $($result.decision)"
        }
    }
    Ok "local pilot traffic generated"

    $metrics = Invoke-IntentHubJson -Method Get -Uri "$baseUrl/api/v1/admin/metrics"
    if ([int]$metrics.totalRequests -ne 10) {
        Fail "totalRequests should be 10; actual=$($metrics.totalRequests)"
    }
    if ([int]$metrics.totalBadCases -ne 4) {
        Fail "totalBadCases should be 4; actual=$($metrics.totalBadCases)"
    }
    Ok "JSON metrics endpoint verified"

    $prometheusResponse = Invoke-WebRequest -Uri "$baseUrl/api/v1/admin/metrics/prometheus" -UseBasicParsing -TimeoutSec 10
    $prometheus = $prometheusResponse.Content
    if ($prometheusResponse.StatusCode -ne 200 -or $prometheus -notmatch "intent_hub_requests_total") {
        Fail "Prometheus metrics endpoint did not expose intent_hub_requests_total"
    }
    if ($prometheus -notmatch "intent_hub_bad_cases_total") {
        Fail "Prometheus metrics endpoint did not expose intent_hub_bad_cases_total"
    }
    Ok "Prometheus metrics endpoint verified"

    $alerts = Invoke-IntentHubJson -Method Get -Uri "$baseUrl/api/v1/admin/metrics/alerts"
    $alertCodes = @($alerts.alerts | ForEach-Object { $_.code })
    if ($alerts.status -ne "WARN" -or $alertCodes -notcontains "BAD_CASE_RATE_HIGH") {
        Fail "alerts endpoint should be WARN with BAD_CASE_RATE_HIGH; actual=$($alerts | ConvertTo-Json -Depth 20 -Compress)"
    }
    Ok "alerts endpoint verified"

    Write-Record -Path $RecordPath -BaseUrl $baseUrl -Metrics $metrics -Prometheus $prometheus -Alerts $alerts -Successes $successes -Rejected $rejected
    Ok "local pilot record written: $RecordPath"
    Write-Host "[INFO] requests=$($metrics.totalRequests) badCases=$($metrics.totalBadCases) status=$($alerts.status) alerts=$($alertCodes -join ',')"
    Write-Host "[OK] local observability pilot smoke completed"
} finally {
    if (-not $KeepRunning) {
        Stop-ProcessIfRunning -Process $script:appProcess -Name "Intent Hub"
    }
}
