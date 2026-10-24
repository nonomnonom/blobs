"""Verify package-download permission and native-library packaging."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import zipfile


ROOT = Path(__file__).resolve().parents[1]
SHA256 = re.compile(r"[a-f0-9]{64}")
SIGNER_CERTIFICATE = re.compile(
    r"^Signer #\d+ certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$",
    re.IGNORECASE | re.MULTILINE,
)


def sdk_directory():
    for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(key):
            return Path(os.environ[key])
    properties = ROOT / "local.properties"
    if properties.exists():
        for line in properties.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                return Path(line.split("=", 1)[1].replace("\\:", ":").replace("\\\\", "\\"))
    raise RuntimeError("Set ANDROID_HOME or configure android/local.properties first.")


def run_tool(path, *arguments):
    result = subprocess.run([str(path), *map(str, arguments)], capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout


def normalized_certificate_sha256(value):
    if not isinstance(value, str) or not SHA256.fullmatch(value.lower()):
        raise ValueError("Expected a 64-character SHA-256 signing certificate fingerprint")
    return value.lower()


def signing_certificate_sha256(signature):
    matches = SIGNER_CERTIFICATE.findall(signature)
    if len(matches) != 1:
        raise RuntimeError("Cannot read exactly one signer certificate SHA-256 fingerprint")
    return matches[0].lower()


def verify(apk, expected_certificate_sha256=None):
    suffix = ".exe" if os.name == "nt" else ""
    build_tools = sdk_directory() / "build-tools" / "36.0.0"
    permissions = run_tool(build_tools / ("aapt2" + suffix), "dump", "permissions", apk)
    internet_permission = bool(
        re.search(r"uses-permission[^\n]*android\.permission\.INTERNET", permissions)
    )
    if not internet_permission:
        raise RuntimeError("The first-run package downloader requires INTERNET permission.")
    badging = run_tool(build_tools / ("aapt2" + suffix), "dump", "badging", apk)
    identity = re.search(
        r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'",
        badging,
        re.MULTILINE,
    )
    if identity is None:
        raise RuntimeError("Cannot read the APK identity and version")

    run_tool(build_tools / ("zipalign" + suffix), "-c", "-P", "16", "-v", "4", apk)
    libraries = []
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if not (name.startswith("lib/") and name.endswith(".so")):
                continue
            data = archive.read(name)
            if data[:6] != b"\x7fELF\x02\x01":
                raise RuntimeError(f"Expected a 64-bit little-endian ELF: {name}")
            offset = struct.unpack_from("<Q", data, 32)[0]
            entry_size, count = struct.unpack_from("<HH", data, 54)
            alignments = []
            for index in range(count):
                header = offset + index * entry_size
                if struct.unpack_from("<I", data, header)[0] != 1:
                    continue
                file_offset, address = struct.unpack_from("<QQ", data, header + 8)
                alignment = struct.unpack_from("<Q", data, header + 48)[0]
                if alignment < 16384 or (address - file_offset) % 16384:
                    raise RuntimeError(f"A load segment is not 16 KB compatible: {name}")
                alignments.append(alignment)
            if not alignments:
                raise RuntimeError(f"No ELF load segments found: {name}")
            libraries.append({"file": name, "load_alignments": alignments})

    abis = sorted({item["file"].split("/")[1] for item in libraries})
    if abis != ["arm64-v8a", "x86_64"]:
        raise RuntimeError(f"Expected ARM64 and x86_64 native libraries, found: {abis}")

    signing_certificate = None
    if expected_certificate_sha256 is not None:
        expected_certificate = normalized_certificate_sha256(expected_certificate_sha256)
        apksigner = build_tools / ("apksigner.bat" if os.name == "nt" else "apksigner")
        signature = run_tool(apksigner, "verify", "--verbose", "--print-certs", apk)
        signing_certificate = signing_certificate_sha256(signature)
        if signing_certificate != expected_certificate:
            raise RuntimeError(
                "APK signer certificate SHA-256 differs from the protected release fingerprint"
            )

    with apk.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()

    report = {
        "apk": apk.name,
        "application_id": identity.group(1),
        "version_code": int(identity.group(2)),
        "version_name": identity.group(3),
        "bytes": apk.stat().st_size,
        "sha256": digest,
        "internet_permission": True,
        "zip_alignment_16kb": True,
        "native_libraries": libraries,
        "scope": "Packaging and permission proof only; source review is required to verify "
        "that networking remains limited to explicit package downloads.",
    }
    if signing_certificate is not None:
        report["signing_certificate_sha256"] = signing_certificate
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--certificate-sha256")
    args = parser.parse_args()
    try:
        report = verify(args.apk.resolve(strict=True), args.certificate_sha256)
        destination = ROOT / "app/build/reports/apk" / (args.apk.stem + ".json")
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(
            f"PASS: {report['apk']}; package-download INTERNET permission; 16 KB ZIP/ELF "
            f"alignment; {len(report['native_libraries'])} native libraries."
        )
        print(f"Report: {destination}")
    except (RuntimeError, OSError, ValueError, struct.error, zipfile.BadZipFile) as error:
        parser.exit(1, f"FAIL: {error}\n")
