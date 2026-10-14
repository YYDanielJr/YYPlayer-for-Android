param([string]$Serial = '127.0.0.1:16384', [string]$AdbPath = 'E:\DeveloperTools\AndroidSDK\platform-tools\adb.exe', [switch]$OnlineQa, [string]$DownloadTree = '')
$ErrorActionPreference = 'Stop'
if (!(Test-Path -LiteralPath $AdbPath)) { $AdbPath = (Get-Command adb -ErrorAction Stop).Source }
$projectDirectory = Split-Path $PSScriptRoot -Parent
Push-Location $projectDirectory
try {
    $buildArguments = @(':app:assembleDebug', ':app:assembleDebugAndroidTest', ':app:testDebugUnitTest', ':app:lintDebug', '--no-daemon', '--max-workers=4')
    if ($OnlineQa) {
        if (!(Test-Path -LiteralPath 'app/build/online-qa/AndroidManifest.xml')) { throw 'Prepare the self-owned HTTPS fixtures first: scripts/Serve-OnlineFixtures.py --prepare' }
        $buildArguments += '-PonlineQa=true'
        & $AdbPath -s $Serial reverse tcp:18443 tcp:18443
        if ($LASTEXITCODE -ne 0) { throw 'QA HTTPS forwarding failed' }
    }
    & .\gradlew.bat @buildArguments
    if ($LASTEXITCODE -ne 0) { throw 'Android build/check failed' }
    $metadataPath = 'app/build/outputs/apk/debug/output-metadata.json'
    $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
    $deviceAbiOutput = & $AdbPath -s $Serial shell getprop ro.product.cpu.abilist
    if ($LASTEXITCODE -ne 0) { throw 'Unable to read device ABIs' }
    $deviceAbis = ($deviceAbiOutput -join '').Trim().Split(',')
    $apkOutput = $null
    foreach ($abi in $deviceAbis) {
        $apkOutput = $metadata.elements | Where-Object { $_.filters | Where-Object { $_.filterType -eq 'ABI' -and $_.value -eq $abi } } | Select-Object -First 1
        if ($null -ne $apkOutput) { break }
    }
    if ($null -eq $apkOutput) { throw "No APK for device ABIs: $deviceAbis" }
    $appApk = Join-Path (Split-Path $metadataPath -Parent) $apkOutput.outputFile
    & $AdbPath -s $Serial install --no-incremental -r $appApk
    if ($LASTEXITCODE -ne 0) { throw 'App update failed' }
    & $AdbPath -s $Serial install --no-incremental -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    if ($LASTEXITCODE -ne 0) { throw 'Test APK update failed' }
    $instrumentArguments = @('-s', $Serial, 'shell', 'am', 'instrument', '-w', '-r')
    if ($OnlineQa) { $instrumentArguments += @('-e', 'onlineQa', 'true') }
    if ($DownloadTree) { $instrumentArguments += @('-e', 'downloadTree', $DownloadTree) }
    $instrumentArguments += 'yy.daniel.yyplayerforandroid.test/androidx.test.runner.AndroidJUnitRunner'
    $testOutput = & $AdbPath @instrumentArguments
    $testExit = $LASTEXITCODE
    $testOutput
    if ($testExit -ne 0 -or !($testOutput -match '^OK \(\d+ tests\)')) { throw 'Device instrumentation did not complete successfully' }
} finally { Pop-Location }
# Deliberately retain both APKs and all app data. Do not use UTP's default uninstall-after-run path.
