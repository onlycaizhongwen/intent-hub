param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$TenantId = "tenant-smoke",
    [string]$SceneId = "dashscope-smoke-scene",
    [string]$Version = "v-dashscope-smoke",
    [string]$Model = "qwen-plus"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($env:DASHSCOPE_API_KEY)) {
    throw "DASHSCOPE_API_KEY is required. Set it only in your shell environment; do not write it to repo files."
}

function Invoke-IntentHubJson {
    param(
        [string]$Method,
        [string]$Uri,
        [object]$Body = $null
    )

    $headers = @{ "Content-Type" = "application/json" }
    if ($null -eq $Body) {
        return Invoke-RestMethod -Method $Method -Uri $Uri -Headers $headers
    }
    $json = $Body | ConvertTo-Json -Depth 20
    return Invoke-RestMethod -Method $Method -Uri $Uri -Headers $headers -Body $json
}

$health = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v1/admin/health"
if ($health.status -ne "UP") {
    throw "Intent Hub health is not UP."
}

$draftBody = @{
    tenantId = $TenantId
    sceneId = $SceneId
    version = $Version
    description = "DashScope controlled LLM smoke"
    actor = "dashscope-smoke"
}
Invoke-IntentHubJson -Method Post -Uri "$BaseUrl/api/v1/admin/config/versions" -Body $draftBody | Out-Null

$strategyBody = @{
    actor = "dashscope-smoke"
    payload = @{
        strategyCode = "dashscope-smoke-strategy"
        confidenceThreshold = 0.95
        llmPolicy = @{
            enabled = $true
            provider = "spring-ai-alibaba"
            model = $Model
            timeoutMs = 5000
            maxRetries = 0
            dailyBudget = 3.0
            fallbackDecision = "REJECTED"
        }
    }
}
Invoke-IntentHubJson -Method Post -Uri "$BaseUrl/api/v1/admin/config/versions/$Version/strategies?tenantId=$TenantId&sceneId=$SceneId" -Body $strategyBody | Out-Null
Invoke-IntentHubJson -Method Post -Uri "$BaseUrl/api/v1/admin/config/versions/$Version/validate?tenantId=$TenantId&sceneId=$SceneId" | Out-Null
Invoke-IntentHubJson -Method Post -Uri "$BaseUrl/api/v1/admin/config/versions/$Version/publish?tenantId=$TenantId&sceneId=$SceneId" -Body @{ actor = "dashscope-smoke" } | Out-Null

$traceId = "TRACE-DASHSCOPE-SMOKE-" + [Guid]::NewGuid().ToString("N")
$recognizeBody = @{
    tenantId = $TenantId
    source = "smoke"
    channel = "cli"
    inputType = "TEXT"
    text = "Please identify the business intent: I want to confirm yesterday's bill status. Return a structured intent recognition result only."
    requestId = "REQ-DASHSCOPE-SMOKE-" + [Guid]::NewGuid().ToString("N")
    traceId = $traceId
    sessionId = "SESSION-DASHSCOPE-SMOKE"
    metadata = @{
        scene_id = $SceneId
    }
    attachments = @()
}

$result = Invoke-IntentHubJson -Method Post -Uri "$BaseUrl/api/v1/intent/recognize" -Body $recognizeBody
$metrics = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v1/admin/metrics"

[PSCustomObject]@{
    traceId = $traceId
    decision = $result.decision
    intentCode = $result.intentCode
    confidence = $result.confidence
    recognitionPath = $result.recognitionPath
    totalLlmBudgetAttempts = $metrics.totalLlmBudgetAttempts
    totalLlmBudgetConsumed = $metrics.totalLlmBudgetConsumed
} | ConvertTo-Json -Depth 10

if (($result.recognitionPath -join ",") -notmatch "LlmRecognizePolicy") {
    throw "LLM path was not observed. Check llmPolicy, profile, and DashScope credentials."
}
