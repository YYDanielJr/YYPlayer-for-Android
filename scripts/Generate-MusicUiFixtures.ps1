param([string]$OutputDirectory = "$env:TEMP\yyplayer-music-ui", [string]$AdbSerial = '127.0.0.1:16384', [switch]$Push)
$ErrorActionPreference = 'Stop'
$ffmpeg = 'E:\DeveloperTools\ffmpeg\bin\ffmpeg.exe'
if (!(Test-Path -LiteralPath $ffmpeg)) { $ffmpeg = (Get-Command ffmpeg -ErrorAction Stop).Source }
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
Add-Type -AssemblyName System.Drawing
function New-Cover([string]$Name, [System.Drawing.Color]$Top, [System.Drawing.Color]$Bottom, [string]$Caption) {
    $bitmap = [System.Drawing.Bitmap]::new(640, 640)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $brush = [System.Drawing.Drawing2D.LinearGradientBrush]::new([System.Drawing.Point]::new(0,0), [System.Drawing.Point]::new(640,640), $Top, $Bottom)
    $graphics.FillRectangle($brush, 0,0,640,640)
    $sun = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(230,247,213,171))
    $graphics.FillEllipse($sun, 198,138,244,244)
    $hill = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(190,30,37,48))
    $graphics.FillEllipse($hill, -180,350,780,550)
    $graphics.FillEllipse($hill, 280,405,600,430)
    $font = [System.Drawing.Font]::new('Segoe UI',20,[System.Drawing.FontStyle]::Regular)
    $graphics.DrawString($Caption,$font,[System.Drawing.Brushes]::White,38,555)
    $graphics.DrawString('YYPLAYER STUDIO',$font,[System.Drawing.Brushes]::White,38,35)
    $bitmap.Save((Join-Path $OutputDirectory $Name),[System.Drawing.Imaging.ImageFormat]::Png)
    $font.Dispose(); $hill.Dispose(); $sun.Dispose(); $brush.Dispose(); $graphics.Dispose(); $bitmap.Dispose()
}
New-Cover 'amber.png' ([System.Drawing.Color]::FromArgb(167,90,52)) ([System.Drawing.Color]::FromArgb(59,51,49)) 'AFTER THE SUNSET'
New-Cover 'violet.png' ([System.Drawing.Color]::FromArgb(84,68,144)) ([System.Drawing.Color]::FromArgb(26,51,83)) 'DRIFT INTO THE NIGHT'
$tracks = @(@{Name='track-file-01.flac';Title='落日之后';Album='暮色之间';Cover='amber.png';Hz='220'},
    @{Name='track-file-02.flac';Title='微光漂浮';Album='夜航';Cover='violet.png';Hz='330'})
foreach ($track in $tracks) {
    & $ffmpeg -hide_banner -loglevel error -y -f lavfi -i "sine=frequency=$($track.Hz):sample_rate=44100:duration=180" -i (Join-Path $OutputDirectory $track.Cover) -map 0:a -map 1:v -af 'volume=0.03' -ac 2 -c:a flac -c:v copy -disposition:v attached_pic -metadata "title=$($track.Title)" -metadata 'artist=YYPlayer Studio' -metadata "album=$($track.Album)" (Join-Path $OutputDirectory $track.Name)
    if ($LASTEXITCODE -ne 0) { throw 'FLAC generation failed' }
}
& $ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'anullsrc=r=44100:cl=stereo' -t 90 -c:a pcm_s16le (Join-Path $OutputDirectory 'no-lyrics.wav')
if ($LASTEXITCODE -ne 0) { throw 'WAV generation failed' }
@'
[ti:落日之后]
[ar:YYPlayer Studio]
[offset:0]
[00:00.00]让晚风慢慢经过
[00:08.00]把一天的喧嚣轻轻放下
[00:16.00]还有一束光 留在心上
[00:24.00]让音乐陪你走向远方
[00:32.00]此刻 只听见你的呼吸
[00:40.00]暮色温柔地落在窗边
[00:48.00]我们在旋律里相遇
[00:56.00]落日之后 仍有微光
[01:04.00]让晚风慢慢经过
[01:12.00]把一天的喧嚣轻轻放下
'@ | Set-Content -LiteralPath (Join-Path $OutputDirectory 'track-file-01.lrc') -Encoding utf8
@'
[00:00.00]微光漂浮在夜色里
[00:10.00]沿着星河 向前走
[00:20.00]每一颗心都有温柔的回声
[00:30.00]在安静的旋律中停留
'@ | Set-Content -LiteralPath (Join-Path $OutputDirectory 'manual-lyrics.lrc') -Encoding utf8
if ($Push) {
    $adb = 'E:\DeveloperTools\AndroidSDK\platform-tools\adb.exe'
    & $adb -s $AdbSerial shell mkdir -p /sdcard/Music/YYPlayerMusicQA
    & $adb -s $AdbSerial push "$OutputDirectory/." /sdcard/Music/YYPlayerMusicQA/
    if ($LASTEXITCODE -ne 0) { throw 'ADB push failed' }
}
Get-ChildItem -LiteralPath $OutputDirectory -File | Get-FileHash -Algorithm SHA256 | Select-Object Path,Hash
