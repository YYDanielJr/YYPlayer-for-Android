"""Generate original fixtures and measure actual on-device circles, never personal video frames.
Requires an installed -PdeviceQa=true debug APK + androidTest APK, adb and ffmpeg.
python scripts/Test-TabletAspect.py --adb <adb> --serial <device>
"""
import argparse
import pathlib
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--adb', default='adb')
parser.add_argument('--serial', required=True)
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parents[1]
out = root / 'build' / 'u22-aspect'
out.mkdir(parents=True, exist_ok=True)

def run(command):
    result = subprocess.run([str(x) for x in command], capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr.decode('utf-8', errors='replace'))
    return result.stdout

def adb(*command):
    return run([args.adb, '-s', args.serial, *command])

def encode(name, width, height, rotate=0, codec='libx264', sar='1'):
    image = out / (name + '.ppm')
    radius = min(width, height) * .195
    data = bytearray()
    for y in range(height):
        for x in range(width):
            data.extend((245, 245, 245) if (x-width/2)**2+(y-height/2)**2 <= radius**2 else (0, 90, 110))
    image.write_bytes(f'P6\n{width} {height}\n255\n'.encode()+data)
    raw = out / (name + '-raw.mp4')
    filters = (['transpose=1'] if rotate else []) + [f'setsar={sar}']
    run(['ffmpeg', '-y', '-loglevel', 'error', '-loop', '1', '-i', image, '-t', '12', '-r', '24',
         '-vf', ','.join(filters), '-c:v', codec, '-preset', 'ultrafast', '-pix_fmt', 'yuv420p', raw])
    target = out / (name + '.mp4')
    run(['ffmpeg', '-y', '-loglevel', 'error', '-display_rotation:v:0', str(rotate), '-i', raw, '-c', 'copy', target])
    return target

fixtures = [('landscape', 640, 360, 0, 'libx264'), ('rotate90', 640, 360, 90, 'libx264'),
            ('rotate270', 640, 360, 270, 'libx264'), ('tablet-hevc90', 2038, 1440, 90, 'libx265')]
for name, w, h, rotation, codec in fixtures:
    fixture = encode(name, w, h, rotation, codec)
    adb('push', fixture, '/sdcard/Android/data/yy.daniel.yyplayerforandroid.qa/files/u22-circle.mp4')
    report = adb('shell', 'am', 'instrument', '-w', '-e', 'class',
                 'yy.daniel.yyplayerforandroid.VideoPhoneDeviceTest#tabletCircleAspectAcrossBackends',
                 '-e', 'fixture', 'u22', 'yy.daniel.yyplayerforandroid.qa.test/androidx.test.runner.AndroidJUnitRunner')
    (out / (name + '-instrumentation.txt')).write_bytes(report)
    evidence = out / name
    evidence.mkdir(exist_ok=True)
    for filename in ('MEDIA3-AUTO.png', 'MPV-AUTO.png', 'MPV-SOFTWARE.png', 'measurements.txt'):
        adb('pull', '/sdcard/Android/data/yy.daniel.yyplayerforandroid.qa/files/u22-evidence/aspect/' + filename,
            evidence / filename)
    print(name, (evidence / 'measurements.txt').read_text(), flush=True)
    if b'OK (1 test)' not in report:
        raise RuntimeError(report.decode('utf-8', errors='replace'))
