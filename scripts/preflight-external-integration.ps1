param(
    [string] $IntentHubBaseUrl = "http://localhost:8080",
    [string] $ModelServiceBaseUrl = "http://localhost:18081",
    [string] $ModelServiceHealthPath = "/health",
    [string] $ModelAuthTokenRef = "",
    [string] $DashScopeSecretRef = "DASHSCOPE_API_KEY",
    [string] $SecretFileRoot = "",
    [switch] $SkipIntentHub,
    [switch] $SkipModelService,
    [switch] $SkipDashScope,
    [switch] $RequireModelAuth,
    [switch] $RequireDashScope,
    [int] $TimeoutSec = 5
)

$ErrorActionPreference = "Stop"

Write-Host "IntentHub external integration preflight"

function Fail {
    param([string] $Message)
    Write-Host "[FAIL] $Message"
    exit 1
}

function Ok {
    param([string] $Message)
    Write-Host "[OK] $Message"
}

function Warn {
    param([string] $Message)
    Write-Host "[WARN] $Message"
}

function Join-Url {
    param(
        [string] $BaseUrl,
        [string] $Path
    )

    $base = $BaseUrl.TrimEnd("/")
    $pathPart = $Path
    if (-not $pathPart.StartsWith("/")) {
        $pathPart = "/" + $pathPart
    }
    return "$base$pathPart"
}

function Resolve-SecretRef {
    param(
        [string] $Ref,
        [string] $FileRoot = ""
    )

    if ([string]::IsNullOrWhiteSpace($Ref)) {
        return [PSCustomObject]@{
            present = $false
            source = "blank"
        }
    }

    $normalized = $Ref.Trim()
    $processValue = [Environment]::GetEnvironmentVariable($normalized, "Process")
    if (-not [string]::IsNullOrWhiteSpace($processValue)) {
        return [PSCustomObject]@{
            present = $true
            source = "environment:process"
        }
    }

    $userValue = [Environment]::GetEnvironmentVariable($normalized, "User")
    if (-not [string]::IsNullOrWhiteSpace($userValue)) {
        return [PSCustomObject]@{
            present = $true
            source = "environment:user"
        }
    }

    $machineValue = [Environment]::GetEnvironmentVariable($normalized, "Machine")
    if (-not [string]::IsNullOrWhiteSpace($machineValue)) {
        return [PSCustomObject]@{
            present = $true
            source = "environment:machine"
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($FileRoot)) {
        try {
            $rootPath = [System.IO.Path]::GetFullPath($FileRoot.Trim())
            $candidatePath = [System.IO.Path]::GetFullPath([System.IO.Path]::Combine($rootPath, $normalized))
            $rootWithSeparator = $rootPath.TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
            $candidateWithSeparator = $candidatePath
            if ((Test-Path -LiteralPath $candidatePath -PathType Container)) {
                $candidateWithSeparator = $candidatePath.TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
            }
            if (-not $candidateWithSeparator.StartsWith($rootWithSeparator, [System.StringComparison]::OrdinalIgnoreCase)) {
                return [PSCustomObject]@{
                    present = $false
                    source = "file:path-traversal-rejected"
                }
            }
            if (Test-Path -LiteralPath $candidatePath -PathType Leaf) {
                $fileValue = Get-Content -LiteralPath $candidatePath -Raw -ErrorAction Stop
                if (-not [string]::IsNullOrWhiteSpace($fileValue)) {
                    return [PSCustomObject]@{
                        present = $true
                        source = "file"
                    }
                }
            }
        } catch {
            return [PSCustomObject]@{
                present = $false
                source = "file:error"
            }
        }
    }

    return [PSCustomObject]@{
        present = $false
        source = "missing"
    }
}

function Invoke-Health {
    param(
        [string] $Name,
        [string] $Uri,
        [hashtable] $Headers = @{}
    )

    try {
        $result = Invoke-RestMethod -Method Get -Uri $Uri -Headers $Headers -TimeoutSec $TimeoutSec
        return [PSCustomObject]@{
            name = $Name
            reachable = $true
            status = $result.status
        }
    } catch {
        return [PSCustomObject]@{
            name = $Name
            reachable = $false
            status = "UNREACHABLE"
            error = $_.Exception.Message
        }
    }
}

$checks = New-Object System.Collections.Generic.List[object]

if (-not $SkipIntentHub) {
    $hubHealthUri = Join-Url -BaseUrl $IntentHubBaseUrl -Path "/api/v1/admin/health"
    $hub = Invoke-Health -Name "intent-hub" -Uri $hubHealthUri
    $checks.Add($hub)
    if (-not $hub.reachable) {
        Fail "Intent Hub health endpoint is unreachable: $hubHealthUri"
    }
    if ($hub.status -and $hub.status -ne "UP") {
        Fail "Intent Hub health is not UP. status=$($hub.status)"
    }
    Ok "Intent Hub health is reachable"
}

$modelSecret = Resolve-SecretRef -Ref $ModelAuthTokenRef -FileRoot $SecretFileRoot
if (-not [string]::IsNullOrWhiteSpace($ModelAuthTokenRef)) {
    if ($modelSecret.present) {
        Ok "model auth secret ref is resolved: $ModelAuthTokenRef ($($modelSecret.source))"
    } elseif ($RequireModelAuth) {
        Fail "model auth secret ref is missing: $ModelAuthTokenRef"
    } else {
        Warn "model auth secret ref is missing: $ModelAuthTokenRef"
    }
} elseif ($RequireModelAuth) {
    Fail "ModelAuthTokenRef is required when RequireModelAuth is enabled"
}

if (-not $SkipModelService) {
    $modelHealthUri = Join-Url -BaseUrl $ModelServiceBaseUrl -Path $ModelServiceHealthPath
    $headers = @{}
    if ($modelSecret.present) {
        $secretValue = [Environment]::GetEnvironmentVariable($ModelAuthTokenRef.Trim(), "Process")
        if ([string]::IsNullOrWhiteSpace($secretValue)) {
            $secretValue = [Environment]::GetEnvironmentVariable($ModelAuthTokenRef.Trim(), "User")
        }
        if ([string]::IsNullOrWhiteSpace($secretValue)) {
            $secretValue = [Environment]::GetEnvironmentVariable($ModelAuthTokenRef.Trim(), "Machine")
        }
        $headers["Authorization"] = "Bearer $secretValue"
    }
    $model = Invoke-Health -Name "model-service" -Uri $modelHealthUri -Headers $headers
    $checks.Add($model)
    if (-not $model.reachable) {
        Fail "model service health endpoint is unreachable: $modelHealthUri"
    }
    if ($model.status -and $model.status -ne "UP") {
        Fail "model service health is not UP. status=$($model.status)"
    }
    Ok "model service health is reachable"
}

if (-not $SkipDashScope) {
    $dashScopeSecret = Resolve-SecretRef -Ref $DashScopeSecretRef -FileRoot $SecretFileRoot
    $checks.Add([PSCustomObject]@{
        name = "dashscope-secret"
        reachable = $dashScopeSecret.present
        status = $(if ($dashScopeSecret.present) { "PRESENT" } else { "MISSING" })
        source = $dashScopeSecret.source
    })
    if ($dashScopeSecret.present) {
        Ok "DashScope secret ref is resolved: $DashScopeSecretRef ($($dashScopeSecret.source))"
    } elseif ($RequireDashScope) {
        Fail "DashScope secret ref is missing: $DashScopeSecretRef"
    } else {
        Warn "DashScope secret ref is missing: $DashScopeSecretRef"
    }
}

[PSCustomObject]@{
    intentHubBaseUrl = $(if ($SkipIntentHub) { "SKIPPED" } else { $IntentHubBaseUrl })
    modelServiceBaseUrl = $(if ($SkipModelService) { "SKIPPED" } else { $ModelServiceBaseUrl })
    modelAuthTokenRef = $(if ([string]::IsNullOrWhiteSpace($ModelAuthTokenRef)) { "UNSET" } else { $ModelAuthTokenRef })
    dashScopeSecretRef = $(if ($SkipDashScope) { "SKIPPED" } else { $DashScopeSecretRef })
    secretFileRoot = $(if ([string]::IsNullOrWhiteSpace($SecretFileRoot)) { "UNSET" } else { "SET" })
    checks = $checks
    note = "Secret values are intentionally never printed. This script can verify shell environment refs and file-mounted refs; JVM -D system properties are verified by runtime smoke tests."
} | ConvertTo-Json -Depth 8

Ok "external integration preflight completed"
