"""Audit the APK actually installed: SHA256, ELF/ZIP 16 KB alignment, SONAME and dependencies.

Usage: python scripts/Verify-MpvArtifact.py path/to/YYPlayer_for_Android_VERSION_ABI_debug.apk output.json
No extraction, downloads, shell execution, or changes to the APK.
"""
import hashlib
import json
from pathlib import Path
import struct
import sys
import zipfile

apk = Path(sys.argv[1])
report = {"apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(), "libraries": []}
required = {"libmpv.so", "libc++_shared.so", "libyy_mpv.so", "libyyplayer_jni.so",
            "libavcodec.so", "libavdevice.so", "libavfilter.so", "libavformat.so",
            "libavutil.so", "libswresample.so", "libswscale.so"}
with zipfile.ZipFile(apk) as archive, apk.open("rb") as stream:
    names = [entry.filename for entry in archive.infolist() if entry.filename.endswith(".so")]
    assert len(names) == len(set(names)), "Duplicate native library paths"
    abis = sorted({name.split("/")[1] for name in names})
    assert abis and set(abis) <= {"arm64-v8a", "x86_64"}, "Unexpected or missing ABI"
    report["abis"] = abis
    for abi in abis:
        assert required <= {Path(name).name for name in names if f"/{abi}/" in name}
    assert all(name.split("/")[1] in ("arm64-v8a", "x86_64") for name in names)
    for name in names:
        entry = archive.getinfo(name)
        data = archive.read(entry)
        assert data[:6] == b"\x7fELF\x02\x01", name
        phoff = struct.unpack_from("<Q", data, 32)[0]
        phsize, phcount = struct.unpack_from("<HH", data, 54)
        headers = [struct.unpack_from("<IIQQQQQQ", data, phoff + i * phsize) for i in range(phcount)]
        loads = [header for header in headers if header[0] == 1]
        assert all(header[7] >= 16384 for header in loads), f"ELF alignment: {name}"
        dynamic = next(header for header in headers if header[0] == 2)
        tags = [struct.unpack_from("<qQ", data, offset) for offset in range(dynamic[2], dynamic[2] + dynamic[5], 16)]
        string_address = next(value for tag, value in tags if tag == 5)
        segment = next(header for header in loads if header[3] <= string_address < header[3] + header[5])
        strings = string_address - segment[3] + segment[2]
        def string(index):
            start = strings + index
            return data[start:data.index(b"\0", start)].decode("utf-8")
        needed = [string(value) for tag, value in tags if tag == 1]
        soname = next((string(value) for tag, value in tags if tag == 14), None)
        stream.seek(entry.header_offset)
        local = stream.read(30)
        filename_size, extra_size = struct.unpack_from("<HH", local, 26)
        zip_offset = entry.header_offset + 30 + filename_size + extra_size
        assert entry.compress_type in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED), f"Unknown compression: {name}"
        # Compressed release libraries are extracted by Android before loading; only stored libs mmap from ZIP.
        if entry.compress_type == zipfile.ZIP_STORED:
            assert zip_offset % 16384 == 0, f"ZIP alignment: {name}"
        report["libraries"].append({"path": name, "sha256": hashlib.sha256(data).hexdigest(),
                                    "soname": soname, "needed": needed,
                                    "load_alignments": [header[7] for header in loads], "zip_offset": zip_offset,
                                    "compressed": entry.compress_type != zipfile.ZIP_STORED})
Path(sys.argv[2]).write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print(f"PASS: {len(names)} native libraries; {', '.join(abis)}; ELF and stored ZIP aligned to 16 KB")
