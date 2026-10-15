"""Self-owned HTTPS API/audio fixtures. Generate a localhost certificate, never alter OS trust."""
import argparse
import datetime
import io
import json
import math
import ssl
import struct
import wave
import subprocess
import shutil
from PIL import Image, ImageDraw
from pathlib import Path
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID
import ipaddress

parser = argparse.ArgumentParser()
parser.add_argument('--prepare', action='store_true')
parser.add_argument('--port', type=int, default=18443)
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
output = root / 'app/build/online-qa'
output.mkdir(parents=True, exist_ok=True)
cert_file, key_file = output / 'ca.pem', output / 'key.pem'
base = f'https://localhost:{args.port}'

if args.prepare:
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, 'YYPlayer Local QA')])
    now = datetime.datetime.now(datetime.timezone.utc)
    cert = (x509.CertificateBuilder().subject_name(subject).issuer_name(subject).public_key(key.public_key())
            .serial_number(x509.random_serial_number()).not_valid_before(now - datetime.timedelta(days=1))
            .not_valid_after(now + datetime.timedelta(days=3))
            .add_extension(x509.BasicConstraints(ca=True, path_length=None), critical=True)
            .add_extension(x509.SubjectAlternativeName([x509.DNSName('localhost'), x509.IPAddress(ipaddress.ip_address('127.0.0.1'))]), critical=False)
            .sign(key, hashes.SHA256()))
    cert_file.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
    key_file.write_bytes(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
    raw = output / 'res/raw'; raw.mkdir(parents=True, exist_ok=True)
    (raw / 'online_qa_ca.pem').write_bytes(cert_file.read_bytes())
    xml = output / 'res/xml'; xml.mkdir(parents=True, exist_ok=True)
    (xml / 'online_qa_network.xml').write_text('''<network-security-config>
    <domain-config cleartextTrafficPermitted="false"><domain includeSubdomains="false">localhost</domain><domain includeSubdomains="false">127.0.0.1</domain>
    <trust-anchors><certificates src="@raw/online_qa_ca" /></trust-anchors></domain-config>
    </network-security-config>''', encoding='utf-8')
    (output / 'AndroidManifest.xml').write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:networkSecurityConfig="@xml/online_qa_network" /></manifest>''', encoding='utf-8')
    config = json.loads((root / 'app/src/main/assets/online-providers.json').read_text(encoding='utf-8'))
    p = config['providers'][0]
    p.update(id='yyplayer-qa', name='YYPlayer 自有 HTTPS 验收', baseUrl=base + '/api',
             headers={'X-QA-Api': 'fixture-only'}, mediaHeaders={'localhost': {'X-QA-Media': 'fixture-media'}},
             requestsPerWindow=1000)
    for quality in p['qualities']:
        quality['extension'] = 'wav'
    (output / 'providers.json').write_text(json.dumps(config, ensure_ascii=False, indent=2), encoding='utf-8')
    print('Prepared explicit QA build resources and config:', output)
    raise SystemExit()

pcm = b''.join(struct.pack('<hh', int(800 * math.sin(2 * math.pi * 220 * n / 44100)), int(800 * math.sin(2 * math.pi * 220 * n / 44100))) for n in range(44100 * 12))
buf = io.BytesIO()
with wave.open(buf, 'wb') as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(44100); w.writeframes(pcm)
audio = buf.getvalue()
wav_file = output / 'original.wav'
wav_file.write_bytes(audio)
flac_file = output / 'original.flac'
encoder = shutil.which('ffmpeg')
if not encoder:
    raise RuntimeError('FLAC fixture requires an existing ffmpeg executable')
subprocess.run([encoder, '-hide_banner', '-loglevel', 'error', '-y', '-i', str(wav_file), '-c:a', 'flac', str(flac_file)], check=True)
flac = flac_file.read_bytes()
cover_buffer = io.BytesIO()
cover_image = Image.new('RGB', (500, 500), '#476b91')
draw = ImageDraw.Draw(cover_image)
draw.ellipse((90, 90, 410, 410), fill='#91b8d2')
draw.ellipse((205, 205, 295, 295), fill='#476b91')
cover_image.save(cover_buffer, format='PNG')
cover = cover_buffer.getvalue()

class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *values):
        with (output / 'requests.log').open('a', encoding='utf-8') as log:
            log.write(fmt % values + '\n')
    def send(self, data, content_type='application/json', status=200, extra=None):
        self.send_response(status)
        self.send_header('Content-Type', content_type)
        self.send_header('Content-Length', str(len(data)))
        for k, v in (extra or {}).items(): self.send_header(k, v)
        self.end_headers()
        try: self.wfile.write(data)
        except (BrokenPipeError, ConnectionResetError): pass
    def do_GET(self):
        parsed = urlparse(self.path)
        params = parse_qs(parsed.query)
        if parsed.path == '/redirect':
            self.send(b'', status=302, extra={'Location': f'https://127.0.0.1:{args.port}/no-header'})
        elif parsed.path == '/no-header':
            self.send(json.dumps({'leaked': self.headers.get('Authorization') is not None}).encode())
        elif parsed.path == '/api':
            if self.headers.get('X-QA-Api') != 'fixture-only':
                self.send(b'{}', status=401); return
            action = params.get('types', [''])[0]
            if action == 'search':
                title = params.get('name', [''])[0]
                songs = [{'id': 'qa1', 'name': title + ' (另一版本)', 'artist': ['QA Ensemble'], 'album': 'Original Fixtures', 'source': 'netease', 'lyric_id': 'qa1', 'duration': 12},
                         {'id': 'qa2', 'name': title, 'artist': ['YYPlayer QA'], 'album': 'Original Fixtures', 'source': 'netease', 'lyric_id': 'qa2', 'duration': 12}]
                if title == '栈返回':
                    songs = [dict(songs[1], id=f'stack{i}', name=f'栈返回 {i:02}') for i in range(1, 33)]
                for song in songs: song['pic_id'] = 'qa-cover'
                self.send(json.dumps(songs, ensure_ascii=False).encode())
            elif action == 'url':
                track = params.get('id', [''])[0]
                br = params.get('br', [''])[0]
                if track == 'fallback' and br == '999': br = '320'
                url = '/mislabelled.mp3' if track == 'mislabelled' else '/audio.wav'
                self.send(json.dumps({'url': base + url, 'br': br if track in ['fallback', 'mislabelled'] else 'PCM WAV fixture; requested ' + br,
                                     'size': len(flac if track == 'mislabelled' else audio) / 1024}).encode())
            elif action == 'pic': self.send(json.dumps({'url': base + '/cover.png'}).encode())
            elif action == 'lyric': self.send(json.dumps({'lyric': '[00:00.00]原创测试歌词\n[00:03.00]通过 HTTPS 获取\n[00:07.00]关闭联网仍可本地读取\n[00:10.00]测试结束'}, ensure_ascii=False).encode())
            else: self.send(b'{}', status=400)
        elif parsed.path == '/cover.png':
            if self.headers.get('X-QA-Media') != 'fixture-media': self.send(b'', status=401); return
            self.send(cover, 'image/png')
        elif parsed.path in ['/audio.wav', '/mislabelled.mp3']:
            if self.headers.get('X-QA-Media') != 'fixture-media': self.send(b'', status=401); return
            payload = flac if parsed.path == '/mislabelled.mp3' else audio
            mime = 'audio/mpeg' if parsed.path == '/mislabelled.mp3' else 'audio/wav'
            range_value = self.headers.get('Range')
            if range_value:
                start, end = range_value.removeprefix('bytes=').split('-')
                start = int(start); end = int(end) if end else len(payload) - 1
                if start >= len(payload): self.send(b'', status=416); return
                self.send(payload[start:end + 1], mime, 206, {'Content-Range': f'bytes {start}-{min(end, len(payload)-1)}/{len(payload)}'})
            else: self.send(payload, mime)
        else: self.send(b'', status=404)

server = ThreadingHTTPServer(('127.0.0.1', args.port), Handler)
tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
tls.load_cert_chain(cert_file, key_file)
server.socket = tls.wrap_socket(server.socket, server_side=True)
print('Self-owned QA HTTPS listening:', base, flush=True)
server.serve_forever()
