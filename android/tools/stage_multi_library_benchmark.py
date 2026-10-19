#!/usr/bin/env python3
"""Download the hash-pinned package set used by the Android retrieval benchmark."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path
from urllib.parse import urlsplit


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_inputs(case_path: Path, catalog_path: Path) -> list[dict[str, object]]:
    case = json.loads(case_path.read_text(encoding="utf-8"))
    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    if case.get("format") != 1 or catalog.get("format") != 1:
        raise ValueError("unsupported case or package catalog format")

    entries = {
        (row["manifest"]["id"], row["manifest"]["version"]): row
        for row in catalog.get("packages", [])
    }
    specs = case.get("libraries")
    if not isinstance(specs, list) or not specs:
        raise ValueError("benchmark case must pin at least one library")

    packages = []
    seen_files: set[str] = set()
    for spec in specs:
        identity = (spec["id"], spec["version"])
        entry = entries.get(identity)
        if entry is None:
            raise ValueError(f"{identity[0]} {identity[1]} is not in the bundled package catalog")
        manifest = entry["manifest"]
        filename = spec["file"]
        if not filename or Path(filename).name != filename or "\\" in filename:
            raise ValueError(f"invalid package filename for {identity[0]}")
        if entry["file_name"] != filename:
            raise ValueError(f"pinned filename mismatch for {identity[0]}")
        if spec["payload_sha256"] != manifest["sha256"]:
            raise ValueError(f"pinned payload digest mismatch for {identity[0]}")
        if entry["download_format"] != "blobs":
            raise ValueError(f"{identity[0]} is not a Blobs library package")
        if filename in seen_files:
            raise ValueError(f"duplicate package filename: {filename}")
        seen_files.add(filename)

        url = urlsplit(entry["url"])
        if not isinstance(entry["download_bytes"], int) or entry["download_bytes"] <= 0:
            raise ValueError(f"invalid pinned package length for {identity[0]}")
        if not re.fullmatch(r"[0-9a-f]{64}", entry["download_sha256"]):
            raise ValueError(f"invalid pinned package digest for {identity[0]}")
        expected_path = re.fullmatch(
            rf"/datasets/nonomnonom/blobs-catalog/resolve/[0-9a-f]{{40}}/packages/{re.escape(filename)}",
            url.path,
        )
        if (
            url.scheme != "https"
            or url.netloc != "huggingface.co"
            or url.query
            or url.fragment
            or not expected_path
        ):
            raise ValueError(f"package URL is not a pinned public HF dataset path: {entry['url']}")

        packages.append(
            {
                "filename": filename,
                "url": entry["url"],
                "bytes": entry["download_bytes"],
                "sha256": entry["download_sha256"],
                "id": identity[0],
                "version": identity[1],
            }
        )
    return packages


def stage(packages: list[dict[str, object]], output_dir: Path) -> list[Path]:
    output_dir.mkdir(parents=True, exist_ok=True)
    staged = []
    for package in packages:
        destination = output_dir / str(package["filename"])
        if destination.is_file() and destination.stat().st_size == package["bytes"]:
            if sha256(destination) == package["sha256"]:
                print(f"verified existing {package['id']} {package['version']}: {destination.name}")
                staged.append(destination)
                continue

        partial = destination.with_name(destination.name + ".partial")
        request = urllib.request.Request(
            str(package["url"]),
            headers={"User-Agent": "Blobs-retrieval-benchmark/1"},
        )
        digest = hashlib.sha256()
        size = 0
        try:
            with (
                urllib.request.urlopen(request, timeout=90) as response,
                partial.open("wb") as output,
            ):
                while chunk := response.read(1024 * 1024):
                    size += len(chunk)
                    if size > package["bytes"]:
                        raise ValueError(f"download exceeded pinned size for {destination.name}")
                    digest.update(chunk)
                    output.write(chunk)
            if size != package["bytes"] or digest.hexdigest() != package["sha256"]:
                raise ValueError(f"download digest or length mismatch for {destination.name}")
            partial.replace(destination)
        except (OSError, urllib.error.URLError, ValueError):
            partial.unlink(missing_ok=True)
            raise

        print(f"downloaded {package['id']} {package['version']}: {destination.name} ({size} bytes)")
        staged.append(destination)
    return staged


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cases", type=Path, required=True)
    parser.add_argument("--catalog", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    try:
        stage(read_inputs(args.cases, args.catalog), args.output_dir)
    except (
        OSError,
        KeyError,
        TypeError,
        ValueError,
        json.JSONDecodeError,
        urllib.error.URLError,
    ) as error:
        print(f"benchmark package staging failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
