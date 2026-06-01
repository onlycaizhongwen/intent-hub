Write-Host "IntentHub P1 environment check"

$java = Get-Command java -ErrorAction SilentlyContinue
if (-not $java) {
    Write-Host "[FAIL] java not found"
    exit 1
}

$javaVersionOutput = & java -version 2>&1 | ForEach-Object { "$_" }
$javaVersionText = ($javaVersionOutput | Out-String).Trim()
Write-Host "[INFO] java -version:"
Write-Host $javaVersionText

if ($javaVersionText -notmatch 'version "(17|18|19|20|21|22|23|24|25)\.') {
    Write-Host "[FAIL] JDK 17+ is required for records, switch expressions, HexFormat, and Spring Boot 4.x"
    exit 1
}

$mvn = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mvn) {
    Write-Host "[FAIL] mvn not found"
    exit 1
}

Write-Host "[INFO] mvn -version:"
& mvn -version

Write-Host "[OK] P1 build environment is ready"
