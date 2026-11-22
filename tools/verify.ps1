param([switch]$DeviceTests)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    & .\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug
    if ($LASTEXITCODE -ne 0) { throw 'Build, unit tests or lint failed. See the Gradle output.' }
    if ($DeviceTests) {
        & .\gradlew.bat --no-daemon connectedDebugAndroidTest
        if ($LASTEXITCODE -ne 0) { throw 'Device tests failed. See app/build/reports/androidTests/connected.' }
    }
    Write-Host 'Verification complete. APK: app/build/outputs/apk/debug/app-debug.apk'
} finally {
    Pop-Location
}
