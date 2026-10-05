param(
    [string]$OutputDirectory = (Join-Path $env:TEMP "yyplayer-mumu-fixtures"),
    [switch]$PushToEmulator,
    [string]$Serial = "emulator-5554"
)

$ErrorActionPreference = "Stop"

$ffmpegCommand = Get-Command ffmpeg -ErrorAction SilentlyContinue
if (-not $ffmpegCommand) {
    throw "FFmpeg was not found on PATH. Install a build with lavfi, libx264, and AAC support."
}

$encoderList = (& $ffmpegCommand.Source -hide_banner -encoders 2>&1 | Out-String)
if ($encoderList -notmatch "libx264") {
    throw "This FFmpeg build has no libx264 encoder. Use an FFmpeg build that includes libx264."
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function Invoke-FixtureFfmpeg([string[]]$Arguments) {
    & $ffmpegCommand.Source @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "FFmpeg failed with exit code $LASTEXITCODE."
    }
}

$wav = Join-Path $OutputDirectory "440hz-stereo-44100.wav"
$landscape = Join-Path $OutputDirectory "test-landscape.mp4"
$portrait = Join-Path $OutputDirectory "test-portrait.mp4"

Invoke-FixtureFfmpeg @(
    "-hide_banner", "-loglevel", "error", "-y",
    "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100:duration=120",
    "-ac", "2", "-c:a", "pcm_s16le", "-ar", "44100", $wav
)

Invoke-FixtureFfmpeg @(
    "-hide_banner", "-loglevel", "error", "-y",
    "-f", "lavfi", "-i", "testsrc2=size=1280x720:rate=24:duration=30",
    "-f", "lavfi", "-i", "sine=frequency=880:sample_rate=48000:duration=30",
    "-c:v", "libx264", "-preset", "ultrafast", "-tune", "zerolatency",
    "-pix_fmt", "yuv420p", "-profile:v", "baseline", "-level:v", "3.1",
    "-c:a", "aac", "-b:a", "96k", "-shortest", $landscape
)

Invoke-FixtureFfmpeg @(
    "-hide_banner", "-loglevel", "error", "-y",
    "-f", "lavfi", "-i", "testsrc2=size=360x640:rate=24:duration=20",
    "-f", "lavfi", "-i", "sine=frequency=660:sample_rate=48000:duration=20",
    "-c:v", "libx264", "-preset", "ultrafast", "-tune", "zerolatency",
    "-pix_fmt", "yuv420p", "-profile:v", "baseline", "-level:v", "3.0",
    "-c:a", "aac", "-b:a", "96k", "-shortest", $portrait
)

$fixtures = @($wav, $landscape, $portrait)
foreach ($fixture in $fixtures) {
    $hash = Get-FileHash -LiteralPath $fixture -Algorithm SHA256
    "{0}  {1}" -f (Split-Path -Leaf $fixture), $hash.Hash
}

if ($PushToEmulator) {
    $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
    if (-not $adbCommand) { throw "adb was not found on PATH." }

    & $adbCommand.Source -s $Serial shell "mkdir -p /sdcard/Movies/YYPlayerQA"
    if ($LASTEXITCODE -ne 0) { throw "Could not create the MuMu QA folder on $Serial." }

    foreach ($fixture in $fixtures) {
        & $adbCommand.Source -s $Serial push $fixture "/sdcard/Movies/YYPlayerQA/$(Split-Path -Leaf $fixture)"
        if ($LASTEXITCODE -ne 0) { throw "Could not push $(Split-Path -Leaf $fixture) to $Serial." }
    }
}
