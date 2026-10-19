"""Publish verified Blobs packages and a signed download index to Hugging Face."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

from build_package import verify_package
from sign_catalog import sign_catalog, validate_catalog

ROOT = Path(__file__).resolve().parents[1]
REPO_ID = "nonomnonom/blobs-catalog"
ASSET_PREFIX = f"https://huggingface.co/datasets/{REPO_ID}/resolve"


def sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def run_hf(*arguments: str, capture_output: bool = False) -> str:
    result = subprocess.run(
        ["hf", *arguments],
        check=True,
        capture_output=capture_output,
        text=True,
    )
    return result.stdout.strip() if result.stdout else ""


def verify_staged_package(path: Path, item: dict) -> None:
    if path.stat().st_size != item.get("download_bytes"):
        raise ValueError(f"Package size differs from the download list: {path.name}")
    if sha256(path) != item.get("download_sha256"):
        raise ValueError(f"Package digest differs from the download list: {path.name}")
    if item.get("download_format") == "blobs" and verify_package(path) != item.get("manifest"):
        raise ValueError(f"Package manifest differs from the download list: {path.name}")


def pin_uploaded_assets(catalog: dict, revision: str, packages_dir: Path) -> dict:
    if not re.fullmatch(r"[a-f0-9]{40}", revision):
        raise ValueError("Hugging Face returned an invalid package revision")
    packages = catalog.get("packages")
    if not isinstance(packages, list) or not packages:
        raise ValueError("The download list has no packages")
    for item in packages:
        name = item.get("file_name")
        if not isinstance(name, str):
            raise ValueError("A package has no filename")
        path = packages_dir / name
        if not path.is_file():
            if item.get("url", "").startswith(
                "https://github.com/nonomnonom/blobs/releases/download/"
            ):
                raise ValueError(f"A GitHub-hosted package is missing from the HF upload: {name}")
            continue
        verify_staged_package(path, item)
        item["url"] = f"{ASSET_PREFIX}/{revision}/packages/{name}"
    return catalog


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--assets",
        type=Path,
        default=ROOT / ".local" / "hf-publish",
        help="A folder containing README.md and packages/ with catalogued assets.",
    )
    parser.add_argument(
        "--private-key",
        type=Path,
        default=Path(
            os.environ.get(
                "BLOBS_CATALOG_SIGNING_KEY",
                str(ROOT / ".local" / "catalog-signing-key.pem"),
            )
        ),
    )
    parser.add_argument(
        "--revision",
        type=int,
        required=True,
        help="New catalog revision; must exceed every previously published revision.",
    )
    args = parser.parse_args()
    assets_dir = args.assets.resolve()
    packages_dir = assets_dir / "packages"
    catalog_path = ROOT / "app" / "src" / "main" / "assets" / "package-catalog.json"
    if not args.private_key.is_file():
        raise SystemExit("Catalog signing key is missing; set BLOBS_CATALOG_SIGNING_KEY.")
    if not (assets_dir / "README.md").is_file() or not packages_dir.is_dir():
        raise SystemExit("The HF upload folder needs README.md and packages/.")
    top_level = {path.name for path in assets_dir.iterdir()}
    if top_level != {"README.md", "packages"}:
        raise SystemExit("The HF upload folder may contain only README.md and packages/.")
    if any(not path.is_file() for path in packages_dir.iterdir()):
        raise SystemExit("The HF packages folder may contain only package files.")

    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    if not catalog.get("revision", 0) < args.revision <= 9_007_199_254_740_991:
        raise SystemExit("Revision must exceed the bundled catalog revision.")
    catalog["revision"] = args.revision
    package_names = {item["file_name"] for item in catalog["packages"]}
    staged_names = {path.name for path in packages_dir.iterdir() if path.is_file()}
    if not staged_names.issubset(package_names):
        raise SystemExit("The HF packages folder contains files outside the download list.")
    validate_catalog(catalog)
    for item in catalog["packages"]:
        path = packages_dir / item["file_name"]
        if path.is_file():
            verify_staged_package(path, item)

    run_hf("repos", "create", REPO_ID, "--type", "dataset", "--public", "--exist-ok")
    run_hf(
        "upload",
        REPO_ID,
        str(assets_dir),
        ".",
        "--type",
        "dataset",
        "--commit-message",
        "Publish verified Blobs models and libraries",
        "--format",
        "json",
    )
    repository = json.loads(
        run_hf("datasets", "info", REPO_ID, "--format", "json", capture_output=True)
    )
    revision = repository.get("sha", "")
    publishable = pin_uploaded_assets(catalog, revision, packages_dir)

    with tempfile.TemporaryDirectory(prefix="blobs-hf-catalog-") as temp:
        catalog_candidate = Path(temp) / "package-catalog.json"
        signed_candidate = Path(temp) / "catalog.signed.json"
        catalog_candidate.write_text(
            json.dumps(publishable, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
            newline="\n",
        )
        sign_catalog(catalog_candidate, signed_candidate, args.private_key.resolve())
        run_hf(
            "upload",
            REPO_ID,
            str(signed_candidate),
            "catalog.signed.json",
            "--type",
            "dataset",
            "--commit-message",
            "Publish signed Blobs download list",
            "--format",
            "json",
        )
        catalog_path.write_bytes(catalog_candidate.read_bytes())
        output = ROOT / ".local" / "catalog.signed.json"
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_bytes(signed_candidate.read_bytes())

    print(f"Published {len(package_names)} packages to {REPO_ID} at {revision}.")
    print(f"Signed download list is available at {ASSET_PREFIX}/main/catalog.signed.json")


if __name__ == "__main__":
    main()
