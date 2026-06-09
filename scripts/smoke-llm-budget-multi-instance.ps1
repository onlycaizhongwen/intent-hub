param(
    [string] $JavaExe,
    [string] $MavenExe,
    [switch] $SkipPackage,
    [switch] $KeepRunning,
    [int] $StartupTimeoutSeconds = 90,
    [int] $PostgresPort = 15436,
    [int] $InstanceAPort = 18084,
    [int] $InstanceBPort = 18085,
    [int] $LlmPort = 18086,
    [int] $RequestCount = 6,
    [double] $DailyBudget = 2.0
)

$ErrorActionPreference = "Stop"

Write-Host "IntentHub LLM budget multi-instance smoke"

$root = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $root "intent-hub-interfaces/target/intent-hub-interfaces-0.1.0-SNAPSHOT.jar"
$logDir = Join-Path $root "target/smoke"
$llmRoot = Join-Path $logDir "llm-budget-service"
$llmScript = Join-Path $llmRoot "mock_llm_server.py"
$pgContainer = "intent-hub-llm-budget-postgres"
$appAProcess = $null
$appBProcess = $null
$llmProcess = $null

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

function Resolve-Python {
    $command = Get-Command python -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }
    $py = Get-Command py -ErrorAction SilentlyContinue
    if ($py) {
        return $py.Source
    }
    Fail "python command not found"
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
    $stdoutLog = Join-Path $logDir "intent-hub-llm-budget-$Name.out.log"
    $stderrLog = Join-Path $logDir "intent-hub-llm-budget-$Name.err.log"
    $arguments = @(
        "-jar", $jarPath,
        "--server.port=$Port",
        "--spring.profiles.active=local-jdbc",
        "--spring.datasource.url=jdbc:postgresql://localhost:$PostgresPort/intent_hub",
        "--intent-hub.model-service.enabled=false",
        "--intent-hub.llm.enabled=true",
        "--intent-hub.llm.base-url=http://localhost:$LlmPort",
        "--intent-hub.llm.timeout-ms=3000",
        "--intent-hub.llm.max-retries=0",
        "--intent-hub.llm.daily-budget=$DailyBudget",
        "--intent-hub.llm.min-confidence=0.70"
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

function Write-MockLlmServer {
    New-Item -ItemType Directory -Force -Path $llmRoot | Out-Null
    Set-Content -Path $llmScript -Encoding ASCII -Value @'
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import sys

class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.0"

    def _send(self, code, payload):
        raw = json.dumps(payload).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(raw)
        self.close_connection = True

    def do_GET(self):
        if self.path == "/health":
            self._send(200, {"status": "UP"})
        else:
            self._send(404, {"error": "not found"})

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        if length:
            self.rfile.read(length)
        if self.path == "/recognize":
            self._send(200, {
                "intentCode": "LLM_HELP",
                "confidence": 0.91,
                "explanation": "mock llm hit"
            })
        else:
            self._send(404, {"error": "not found"})

    def log_message(self, fmt, *args):
        return

if __name__ == "__main__":
    port = int(sys.argv[1])
    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
'@
}

function Invoke-RecognitionJob {
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
        channel = "llm-budget-multi-instance"
        inputType = "TEXT"
        requestId = $RequestId
        traceId = $TraceId
        text = "please understand this unknown request $RequestId"
        metadata = @{ scene_id = $SceneId }
        attachments = @()
    } | ConvertTo-Json -Depth 10 -Compress
    Invoke-RestMethod -Method Post -Uri "http://localhost:$Port/api/v1/intent/recognize" -ContentType "application/json" -Body $body -TimeoutSec 15
}

try {
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    Assert-Port-Free $PostgresPort
    Assert-Port-Free $InstanceAPort
    Assert-Port-Free $InstanceBPort
    Assert-Port-Free $LlmPort

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

    $python = Resolve-Python
    Write-MockLlmServer
    $llmStdout = Join-Path $logDir "mock-llm-budget.out.log"
    $llmStderr = Join-Path $logDir "mock-llm-budget.err.log"
    $script:llmProcess = Start-Process -FilePath $python -ArgumentList @($llmScript, $LlmPort) -WorkingDirectory $llmRoot -WindowStyle Hidden -RedirectStandardOutput $llmStdout -RedirectStandardError $llmStderr -PassThru
    Ok "mock LLM service started: port=$LlmPort pid=$($script:llmProcess.Id)"
    $llmHealth = Wait-HttpJson -Uri "http://localhost:$LlmPort/health" -TimeoutSeconds $StartupTimeoutSeconds
    if ($llmHealth.status -ne "UP") {
        Fail "mock LLM health is not UP"
    }
    Ok "mock LLM health is UP"

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

    $tenantId = "tenant-llm-budget-smoke"
    $sceneId = "llm-budget-smoke-scene"
    $version = "v-llm-budget-smoke"

    Invoke-IntentHubJson -Method Post -Uri "http://localhost:$InstanceAPort/api/v1/admin/config/versions" -Body @{
        tenantId = $tenantId
        sceneId = $sceneId
        version = $version
        description = "llm budget multi-instance smoke"
        actor = "llm-budget-smoke"
    } | Out-Null

    Invoke-IntentHubJson -Method Post -Uri "http://localhost:$InstanceAPort/api/v1/admin/config/versions/$version/intents?tenantId=$tenantId&sceneId=$sceneId" -Body @{
        actor = "llm-budget-smoke"
        payload = @{
            intentCode = "NEVER_RULE_HIT"
            intentName = "Rule should not hit"
            enabled = $true
            definition = @{
                matchType = "CONTAINS"
                pattern = "never-match-by-rule"
                confidence = 0.91
                explanation = "rule should not match"
            }
        }
    } | Out-Null

    Invoke-IntentHubJson -Method Post -Uri "http://localhost:$InstanceAPort/api/v1/admin/config/versions/$version/intents?tenantId=$tenantId&sceneId=$sceneId" -Body @{
        actor = "llm-budget-smoke"
        payload = @{
            intentCode = "LLM_HELP"
            intentName = "LLM fallback help"
            enabled = $true
            definition = @{
                matchType = "CONTAINS"
                pattern = "never-match-llm-help-by-rule"
                confidence = 0.91
                explanation = "LLM returned intent only"
            }
        }
    } | Out-Null

    Invoke-IntentHubJson -Method Post -Uri "http://localhost:$InstanceAPort/api/v1/admin/config/versions/$version/downstream-actions?tenantId=$tenantId&sceneId=$sceneId" -Body @{
        actor = "llm-budget-smoke"
        payload = @{
            actionCode = "LLM_HELP_API"
            actionType = "API"
            target = "llm.help"
            idempotencyRequired = $false
            timeoutMs = 1500
            actionSchema = @{ intentCode = "LLM_HELP" }
        }
    } | Out-Null

    Invoke-IntentHubJson -Method Post -Uri "http://localhost:$InstanceAPort/api/v1/admin/config/versions/$version/strategies?tenantId=$tenantId&sceneId=$sceneId" -Body @{
        actor = "llm-budget-smoke"
        payload = @{
            strategyCode = "default"
            confidenceThreshold = 0.60
            modelPolicy = @{ enabled = $false }
            llmPolicy = @{
                enabled = $true
                provider = "http-contract"
                model = "mock-llm"
                timeoutMs = 3000
                maxRetries = 0
                dailyBudget = $DailyBudget
                fallbackDecision = "REJECTED"
            }
        }
    } | Out-Null

    $validation = Invoke-IntentHubJson -Method Post -Uri "http://localhost:$InstanceAPort/api/v1/admin/config/versions/$version/validate?tenantId=$tenantId&sceneId=$sceneId"
    if (-not $validation.valid) {
        Fail "config validation failed: $($validation.errors -join '; ')"
    }
    Invoke-IntentHubJson -Method Post -Uri "http://localhost:$InstanceAPort/api/v1/admin/config/versions/$version/publish?tenantId=$tenantId&sceneId=$sceneId" -Body @{
        actor = "llm-budget-smoke"
    } | Out-Null
    Ok "LLM budget smoke config published"

    $jobs = @()
    for ($i = 1; $i -le $RequestCount; $i++) {
        $port = if ($i % 2 -eq 0) { $InstanceAPort } else { $InstanceBPort }
        $jobs += Start-Job -ScriptBlock ${function:Invoke-RecognitionJob} -ArgumentList $port, $tenantId, $sceneId, "req-llm-budget-$i", "trace-llm-budget-$i"
    }
    Wait-Job -Job $jobs | Out-Null
    $results = @()
    foreach ($job in $jobs) {
        $results += Receive-Job -Job $job
        if ($job.State -ne "Completed") {
            Fail "recognition job failed: $($job.State)"
        }
    }
    Remove-Job -Job $jobs -Force

    $llmSuccesses = @($results | Where-Object { $_.intentCode -eq "LLM_HELP" -and $_.recognitionPath -contains "LlmRecognizePolicy" }).Count
    $fallbacks = @($results | Where-Object { $_.recognitionPath -contains "LLM_FALLBACK:REJECTED" }).Count
    if ($llmSuccesses -ne [int]$DailyBudget) {
        $actual = $results | ConvertTo-Json -Depth 20 -Compress
        Fail "LLM successes should equal daily budget $DailyBudget; actual successes=$llmSuccesses results=$actual"
    }
    if ($fallbacks -lt ($RequestCount - [int]$DailyBudget)) {
        Fail "LLM fallback count should be at least exhausted requests; actual: $fallbacks"
    }
    Ok "LLM budget gate limited successful LLM calls"

    $budgetUsage = Invoke-IntentHubJson -Method Get -Uri "http://localhost:$InstanceAPort/api/v1/admin/llm/budget-usage?tenantId=$tenantId&sceneId=$sceneId"
    if ([double]$budgetUsage.reservedUnits -gt $DailyBudget) {
        Fail "reserved budget usage exceeded daily budget: $($budgetUsage | ConvertTo-Json -Compress)"
    }
    if ([double]$budgetUsage.pendingUnits -ne 0.0) {
        Fail "budget usage has pending units after successful calls: $($budgetUsage | ConvertTo-Json -Compress)"
    }
    if ([double]$budgetUsage.consumedUnits -lt $llmSuccesses) {
        Fail "confirmed external attempts should cover successful LLM calls: $($budgetUsage | ConvertTo-Json -Compress)"
    }
    Ok "admin reserved budget stays within daily budget and pending units are zero"

    $budgetRows = Invoke-Postgres "select count(*) from llm_budget_usage where tenant_id = '$tenantId' and scene_id = '$sceneId';"
    if ($budgetRows -ne "2") {
        Fail "llm_budget_usage should have budget row and provider row only; actual rows: $budgetRows"
    }
    $budgetDetails = Invoke-Postgres "select provider || '/' || model || ':' || attempt_count || ':' || consumed_units from llm_budget_usage where tenant_id = '$tenantId' and scene_id = '$sceneId' order by provider, model;"
    Write-Host "[INFO] budget rows: $budgetDetails"
    $traceCount = Invoke-Postgres "select count(*) from recognition_trace where tenant_id = '$tenantId' and scene_id = '$sceneId';"
    if ([int]$traceCount -ne $RequestCount) {
        Fail "recognition_trace count should match request count; actual: $traceCount"
    }
    Ok "database budget rows and trace count are consistent"

    Write-Host "[OK] LLM budget multi-instance smoke completed"
} finally {
    Stop-ProcessIfRunning -Process $script:appAProcess -Name "Intent Hub instance a"
    Stop-ProcessIfRunning -Process $script:appBProcess -Name "Intent Hub instance b"
    Stop-ProcessIfRunning -Process $script:llmProcess -Name "mock LLM service"
    Stop-Postgres
    if (-not $KeepRunning -and (Test-Path $llmRoot)) {
        Remove-Item -LiteralPath $llmRoot -Recurse -Force -ErrorAction SilentlyContinue
        Ok "mock LLM files removed"
    }
}
