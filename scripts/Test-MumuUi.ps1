param([string]$Serial = '127.0.0.1:16384', [string]$AdbPath = 'E:\DeveloperTools\AndroidSDK\platform-tools\adb.exe')
$ErrorActionPreference = 'Stop'
if (!(Test-Path -LiteralPath $AdbPath)) { $AdbPath = (Get-Command adb -ErrorAction Stop).Source }
$projectDirectory = Split-Path $PSScriptRoot -Parent
Push-Location $projectDirectory
try {
    & .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4
    if ($LASTEXITCODE -ne 0) { throw 'Android build/check failed' }
    & $AdbPath -s $Serial install -r app/build/outputs/apk/debug/app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'App update failed' }
    & $AdbPath -s $Serial install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    if ($LASTEXITCODE -ne 0) { throw 'Test APK update failed' }
    $testOutput = & $AdbPath -s $Serial shell am instrument -w -r yy.daniel.yyplayerforandroid.test/androidx.test.runner.AndroidJUnitRunner
    $testExit = $LASTEXITCODE
    $testOutput
    if ($testExit -ne 0 -or !($testOutput -match '^OK \(\d+ tests\)')) { throw 'Device instrumentation did not complete successfully' }
} finally { Pop-Location }
# Deliberately retain both APKs and all app data. Do not use UTP's default uninstall-after-run path.
