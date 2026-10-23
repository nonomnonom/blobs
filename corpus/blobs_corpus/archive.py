"""Immutable .blobs archive contract shared by builders and evaluators."""

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import tempfile
import zipfile


MAX_MODEL_PAYLOAD_BYTES = 40_000_000_000
MAX_LIBRARY_PAYLOAD_BYTES = 6 * 1024**3


def payload_limit(kind: str) -> int:
    if kind == "model":
        return MAX_MODEL_PAYLOAD_BYTES
    if kind == "library":
        return MAX_LIBRARY_PAYLOAD_BYTES
    raise ValueError("Invalid package kind")


def valid_metadata_text(value: object, limit: int) -> bool:
    if not isinstance(value, str):
        return False
    try:
        utf16_length = len(value.encode("utf-16-le", "strict")) // 2
    except UnicodeError:
        return False
    return (
        bool(value.strip())
        and "\x00" not in value
        and "\ufffd" not in value
        and 1 <= utf16_length <= limit
    )


def verify_package(path: Path):
    """Check the streamed archive payload against the Android package contract."""
    with zipfile.ZipFile(path) as archive:
        if not archive.namelist() or archive.namelist()[0] != "manifest.json":
            raise ValueError("Package manifest must be first")
        if archive.getinfo("manifest.json").file_size > 65536:
            raise ValueError("Package manifest exceeds 65,536 bytes")
        manifest = json.loads(archive.read("manifest.json"))
        if (
            not isinstance(manifest, dict)
            or type(manifest.get("format")) is not int
            or manifest["format"] != 1
        ):
            raise ValueError("Unsupported package format")
        for field, limit in [
            ("id", 64),
            ("version", 40),
            ("kind", 7),
            ("sha256", 64),
            ("title", 160),
            ("license", 4000),
            ("origin", 2000),
        ]:
            value = manifest.get(field)
            if not valid_metadata_text(value, limit):
                raise ValueError(f"Invalid package {field}")
        if (
            not re.fullmatch("[a-z0-9-]{1,64}", manifest["id"])
            or not re.fullmatch("[a-zA-Z0-9.-]{1,40}", manifest["version"])
            or not re.fullmatch("[a-f0-9]{64}", manifest["sha256"])
            or manifest["kind"] not in {"model", "library"}
            or type(manifest.get("bytes")) is not int
            or not 0 < manifest["bytes"] <= payload_limit(manifest["kind"])
        ):
            raise ValueError("Invalid package identity or payload size")
        records_sha256 = manifest.get("records_sha256")
        if "records_sha256" in manifest and (
            manifest["kind"] != "library" or not isinstance(records_sha256, str)
            or not re.fullmatch("[a-f0-9]{64}", records_sha256)
        ):
            raise ValueError("Invalid package records digest")
        payload = "model.gguf" if manifest["kind"] == "model" else "library.sqlite"
        if archive.namelist() != ["manifest.json", payload]:
            raise ValueError("Unexpected package contents")
        if archive.getinfo(payload).file_size != manifest["bytes"]:
            raise ValueError("Package payload length disagrees with manifest")
        count, digest = 0, hashlib.sha256()
        with archive.open(payload) as stream:
            while block := stream.read(1024 * 1024):
                count += len(block)
                if count > manifest["bytes"]:
                    raise ValueError("Package payload exceeds declared length")
                digest.update(block)
        if count != manifest["bytes"] or digest.hexdigest() != manifest["sha256"]:
            raise ValueError("Invalid package hash or byte count")
        return manifest


def create_package(
    payload: Path,
    output: Path,
    *,
    kind: str,
    id: str,
    version: str,
    title: str,
    license: str,
    origin: str,
    records_sha256: str | None = None,
):
    """Write a new archive atomically and return its immutable manifest."""
    if kind not in {"model", "library"}:
        raise ValueError("Invalid package kind")
    if not re.fullmatch("[a-z0-9-]{1,64}", id) or not re.fullmatch(
        "[a-zA-Z0-9.-]{1,40}", version
    ):
        raise ValueError("Invalid package identity")
    if not all(
        valid_metadata_text(text, limit)
        for text, limit in [(title, 160), (license, 4000), (origin, 2000)]
    ):
        raise ValueError("Package metadata exceeds Android import limits")
    if records_sha256 is not None and (
        kind != "library" or not re.fullmatch("[a-f0-9]{64}", records_sha256)
    ):
        raise ValueError("Invalid library records digest")
    if not 0 < payload.stat().st_size <= payload_limit(kind):
        raise ValueError(f"Payload exceeds {kind} package byte limit")
    if output.exists():
        raise FileExistsError(output)
    if kind == "model":
        with payload.open("rb") as stream:
            if stream.read(4) != b"GGUF":
                raise ValueError("Expected GGUF")
    with payload.open("rb") as stream:
        checksum = hashlib.file_digest(stream, "sha256").hexdigest()
    manifest = dict(
        format=1,
        id=id,
        version=version,
        kind=kind,
        title=title,
        bytes=payload.stat().st_size,
        sha256=checksum,
        license=license,
        origin=origin,
    )
    if records_sha256 is not None:
        manifest["records_sha256"] = records_sha256
    output.parent.mkdir(parents=True, exist_ok=True)
    compression = zipfile.ZIP_DEFLATED if kind == "library" else zipfile.ZIP_STORED
    with tempfile.TemporaryDirectory(prefix=".package-", dir=output.parent) as folder:
        staged = Path(folder) / "package.blobs"
        with zipfile.ZipFile(staged, "x", allowZip64=True) as archive:
            header = zipfile.ZipInfo("manifest.json", date_time=(1980, 1, 1, 0, 0, 0))
            archive.writestr(header, json.dumps(manifest, ensure_ascii=False, sort_keys=True))
            info = zipfile.ZipInfo(
                "model.gguf" if kind == "model" else "library.sqlite",
                date_time=(1980, 1, 1, 0, 0, 0),
            )
            info.compress_type = compression
            info.file_size = payload.stat().st_size
            with (
                payload.open("rb") as source,
                archive.open(info, "w", force_zip64=info.file_size >= zipfile.ZIP64_LIMIT) as dest,
            ):
                shutil.copyfileobj(source, dest, 1024 * 1024)
        with staged.open("r+b") as completed:
            completed.flush()
            os.fsync(completed.fileno())
        os.link(staged, output)
    return manifest


# Kept while callers move from the former all-in-one package module.
def package(*args, **kwargs):
    return create_package(*args, **kwargs)
