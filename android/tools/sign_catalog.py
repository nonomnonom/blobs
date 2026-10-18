"""Sign a Blobs package catalog with the private catalog release key."""

from __future__ import annotations

import argparse
import base64
import json
import os
from pathlib import Path
import re
from urllib.parse import urlparse

MAX_CATALOG_BYTES = 1_048_576
RELEASE_ROOT = "https://github.com/nonomnonom/blobs/releases/download"
MODEL_FORMATS = {"gguf", "blobs"}
LIBRARY_FORMATS = {"blobs"}


def validate_catalog(parsed: object) -> None:
    if not isinstance(parsed, dict) or parsed.get("format") != 1:
        raise SystemExit("Catalog schema is not supported.")
    release_tag = parsed.get("release_tag")
    if not isinstance(release_tag, str) or not re.fullmatch(r"v\d+\.\d+\.\d+", release_tag):
        raise SystemExit("Catalog release_tag must be a semantic version tag.")
    packages = parsed.get("packages")
    if not isinstance(packages, list) or not packages:
        raise SystemExit("Catalog must contain packages.")

    keys: set[tuple[str, str, str]] = set()
    roles: list[str] = []
    has_model = False
    for item in packages:
        if not isinstance(item, dict):
            raise SystemExit("Each catalog package must be an object.")
        manifest = item.get("manifest")
        compatibility = item.get("compatibility")
        if not isinstance(manifest, dict) or not isinstance(compatibility, dict):
            raise SystemExit("Each package needs manifest and compatibility metadata.")
        kind, role = manifest.get("kind"), item.get("role")
        if kind not in {"model", "library"} or role not in {"model", "starter", "optional"}:
            raise SystemExit("Package kind or role is invalid.")
        if (kind == "model") != (role == "model"):
            raise SystemExit("Model packages must use the model role; libraries cannot.")
        file_name = item.get("file_name")
        download_format = item.get("download_format")
        allowed_formats = MODEL_FORMATS if kind == "model" else LIBRARY_FORMATS
        if download_format not in allowed_formats:
            raise SystemExit("Package download format does not match its kind.")
        file_name_pattern = (
            r"[a-z0-9][a-z0-9.-]{0,100}\.blobs"
            if download_format == "blobs"
            else r"[a-zA-Z0-9][a-zA-Z0-9_.-]{0,120}\.gguf"
        )
        if not isinstance(file_name, str) or not re.fullmatch(file_name_pattern, file_name):
            raise SystemExit("Package filename does not match its download format.")
        if not isinstance(item.get("download_bytes"), int) or not 1 <= item["download_bytes"] <= 40_000_000_000:
            raise SystemExit("Package download size is invalid.")
        digest = item.get("download_sha256")
        manifest_digest = manifest.get("sha256")
        if not isinstance(digest, str) or not re.fullmatch(r"[a-f0-9]{64}", digest):
            raise SystemExit("Package download SHA-256 is invalid.")
        if not isinstance(manifest_digest, str) or not re.fullmatch(r"[a-f0-9]{64}", manifest_digest):
            raise SystemExit("Package manifest SHA-256 is invalid.")
        if manifest.get("format") != 1:
            raise SystemExit("Package manifest format is not supported.")
        if not isinstance(manifest.get("bytes"), int) or not 1 <= manifest["bytes"] <= 40_000_000_000:
            raise SystemExit("Package manifest size is invalid.")
        for field, limit in (("title", 160), ("license", 4000), ("origin", 2000)):
            value = manifest.get(field)
            if not isinstance(value, str) or not 1 <= len(value) <= limit:
                raise SystemExit(f"Package manifest {field} is missing or invalid.")
        url = item.get("url")
        if not isinstance(url, str):
            raise SystemExit("Package URL is invalid.")
        parsed_url = urlparse(url)
        pinned_hf = parsed_url.scheme == "https" and parsed_url.hostname == "huggingface.co" and re.search(r"/resolve/[a-f0-9]{40}/", parsed_url.path)
        pinned_release = url == f"{RELEASE_ROOT}/{release_tag}/{file_name}"
        if not (pinned_hf or pinned_release):
            raise SystemExit("Package URL must be a pinned Hugging Face revision or matching release asset.")
        package_id, version = manifest.get("id"), manifest.get("version")
        if not isinstance(package_id, str) or not re.fullmatch(r"[a-z0-9-]{1,64}", package_id):
            raise SystemExit("Package manifest ID is invalid.")
        if not isinstance(version, str) or not re.fullmatch(r"[a-zA-Z0-9.-]{1,40}", version):
            raise SystemExit("Package manifest version is invalid.")
        key = (package_id, version, manifest_digest[:16])
        if key in keys:
            raise SystemExit("Package manifest keys must be unique.")
        keys.add(key)
        for field, limit in (("description", 2000), ("language", 64), ("publisher", 160)):
            value = item.get(field)
            if not isinstance(value, str) or not value.strip() or len(value) > limit:
                raise SystemExit(f"Package {field} metadata is missing or invalid.")
        minimum_app = compatibility.get("minimum_app_version")
        if not isinstance(minimum_app, str) or not re.fullmatch(r"\d+\.\d+\.\d+", minimum_app):
            raise SystemExit("Package minimum_app_version must be a semantic version.")
        if kind == "model":
            has_model = True
            if (
                compatibility.get("runtime") != "llama.cpp"
                or compatibility.get("format") != "GGUF"
                or not isinstance(compatibility.get("minimum_ram_mb"), int)
                or compatibility["minimum_ram_mb"] < 1
                or not isinstance(item.get("recommended_up_to_memory_mb"), int)
                or item["recommended_up_to_memory_mb"] < compatibility["minimum_ram_mb"]
            ):
                raise SystemExit("Model compatibility metadata is invalid.")
        elif compatibility.get("format") != "Blobs library archive" or compatibility.get("corpus_schema") != 3 or compatibility.get("retrieval_format") != "SQLite FTS5":
            raise SystemExit("Library compatibility metadata is invalid.")
        roles.append(role)
    if not has_model or roles.count("starter") != 1:
        raise SystemExit("Catalog requires at least one model and exactly one starter library.")


def sign_catalog(catalog_path: Path, output_path: Path, private_key_path: Path) -> None:
    try:
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import ec
    except ImportError as error:
        raise SystemExit(
            "Signing requires cryptography. Run with `uv run --locked --project ../corpus "
            "--group publisher python tools/sign_catalog.py`."
        ) from error

    payload = catalog_path.read_bytes()
    if not payload or len(payload) > MAX_CATALOG_BYTES:
        raise SystemExit("Catalog must be between 1 byte and 1 MiB.")
    try:
        parsed = json.loads(payload)
    except json.JSONDecodeError as error:
        raise SystemExit(f"Catalog JSON is invalid: {error}") from error
    validate_catalog(parsed)

    key = serialization.load_pem_private_key(private_key_path.read_bytes(), password=None)
    if not isinstance(key, ec.EllipticCurvePrivateKey) or not isinstance(
        key.curve, ec.SECP256R1
    ):
        raise SystemExit("Catalog signing key must be an unencrypted P-256 EC private key.")
    signature = key.sign(payload, ec.ECDSA(hashes.SHA256()))
    envelope = {
        "format": 1,
        "payload": base64.b64encode(payload).decode("ascii"),
        "signature": base64.b64encode(signature).decode("ascii"),
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(envelope, separators=(",", ":")) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("catalog", type=Path, help="Unsigned package-catalog.json")
    parser.add_argument("output", type=Path, help="Signed catalog envelope output")
    parser.add_argument(
        "--private-key",
        type=Path,
        default=os.environ.get("BLOBS_CATALOG_SIGNING_KEY"),
        help="Private-key PEM path (or BLOBS_CATALOG_SIGNING_KEY)",
    )
    args = parser.parse_args()
    if args.private_key is None:
        parser.error("pass --private-key or set BLOBS_CATALOG_SIGNING_KEY")
    sign_catalog(args.catalog, args.output, args.private_key)


if __name__ == "__main__":
    main()
