"""Compile the app's download catalog from reviewed supply admissions, never from a hand-kept list.

The bundled catalog is what an installed app advertises. Hand-editing it lets the app offer
libraries that the supply registry has retired, so this tool derives every library entry from a
`release_ready` admission and its archive receipt, then proves the result against the same
publisher contract that gates an actual upload.
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

from publish_hf_assets import (
    ASSET_PREFIX,
    ADMISSION_EVIDENCE_PATHS,
    read_release_admissions,
    require_release_admissions,
    supply_evidence_path,
    validate_archive_receipt,
)
from sign_catalog import validate_catalog

ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = ROOT.parent
REGISTRY = REPOSITORY / "corpus" / "supply" / "registry.json"
MODELS_LOCK = ROOT / "models.lock.json"
MODELS_PRESENTATION = ROOT / "models.present.json"
CATALOG = ROOT / "app" / "src" / "main" / "assets" / "package-catalog.json"
RELEASE_TAG = re.compile(r"v\d+\.\d+\.\d+")
ASSET_REVISION = re.compile(r"[a-f0-9]{40}")

# Kiwix edition names carry the language as an ISO 639-1 code; an unmapped code stops the build
# rather than labelling a library with a language nobody reviewed.
LANGUAGE_NAMES = {
    "de": "German",
    "en": "English",
    "es": "Spanish",
    "fr": "French",
    "id": "Indonesian",
    "it": "Italian",
    "ja": "Japanese",
    "nl": "Dutch",
    "pl": "Polish",
    "pt": "Portuguese",
    "ru": "Russian",
    "zh": "Chinese",
}


def read_json(path: Path) -> dict:
    if not path.is_file():
        raise SystemExit(f"Required input is missing: {path}")
    try:
        parsed = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise SystemExit(f"Required input is invalid JSON: {path}") from error
    if not isinstance(parsed, dict):
        raise SystemExit(f"Required input must be an object: {path}")
    return parsed


def library_language(lock: dict) -> str:
    declared = lock.get("language")
    if isinstance(declared, str) and declared:
        name = LANGUAGE_NAMES.get(declared.lower(), declared)
        return name
    identities = [lock.get("id", "")] + [
        archive.get("id", "") for archive in lock.get("archives", []) if isinstance(archive, dict)
    ]
    codes = {
        match.group(1)
        for identity in identities
        for match in re.finditer(r"(?:^|[-_.])([a-z]{2})(?:[-_.]|$)", identity)
        if match.group(1) in LANGUAGE_NAMES
    }
    if len(codes) != 1:
        raise SystemExit(
            "Source lock language is not resolvable from the edition identity; "
            f"declare a `language` field. Found {sorted(codes) or 'none'} in {identities}"
        )
    return LANGUAGE_NAMES[codes.pop()]


def model_packages(minimum_app_version: str) -> list[dict]:
    lock = read_json(MODELS_LOCK)
    presentation = read_json(MODELS_PRESENTATION)
    entries = presentation.get("models")
    if not isinstance(entries, dict):
        raise SystemExit("models.present.json must declare a models object")
    packages: list[dict] = []
    for key, model in lock.items():
        if key == "format" or not isinstance(model, dict):
            continue
        package_id = model.get("id")
        copy = entries.get(package_id)
        if not isinstance(copy, dict):
            raise SystemExit(f"Model {package_id} has no entry in models.present.json")
        # A locked model is not an advertised one. Publication stays an explicit per-model
        # declaration so a local experiment cannot reach the catalog by editing the lock alone.
        publication = copy.get("publication")
        if publication not in {"authorized", "local-only"}:
            raise SystemExit(
                f"Model {package_id} has no publication state in models.present.json; "
                'declare "authorized" or "local-only"'
            )
        if publication != "authorized":
            continue
        for field in ("revision", "repository", "file", "bytes", "sha256", "license", "title"):
            if not isinstance(model.get(field), (str, int)):
                raise SystemExit(f"Model {package_id} lock is missing {field}")
        for field in (
            "description",
            "language",
            "publisher",
            "minimum_ram_mb",
            "recommended_up_to_memory_mb",
        ):
            if not isinstance(copy.get(field), (str, int)):
                raise SystemExit(f"Model {package_id} presentation is missing {field}")
        packages.append(
            {
                "file_name": model["file"],
                "url": (
                    f"https://huggingface.co/{model['repository']}/resolve/"
                    f"{model['revision']}/{model['file']}"
                ),
                "download_format": "gguf",
                "download_bytes": model["bytes"],
                "download_sha256": model["sha256"],
                "manifest": {
                    "format": 1,
                    "id": model["id"],
                    "version": model["revision"][:8],
                    "kind": "model",
                    "title": copy.get("title", model["title"]),
                    "bytes": model["bytes"],
                    "sha256": model["sha256"],
                    "license": model["license"],
                    "origin": (
                        f"https://huggingface.co/{model['repository']}/tree/{model['revision']}"
                    ),
                },
                "role": "model",
                "recommended_up_to_memory_mb": copy["recommended_up_to_memory_mb"],
                "description": copy["description"],
                "language": copy["language"],
                "publisher": copy["publisher"],
                "compatibility": {
                    "runtime": "llama.cpp",
                    "format": "GGUF",
                    "minimum_ram_mb": copy["minimum_ram_mb"],
                    "minimum_app_version": minimum_app_version,
                },
            }
        )
    if not packages:
        raise SystemExit("no locked model is declared authorized for publication")
    return packages


def library_description(profile: dict, records: int) -> str:
    description = f"{records:,} records. {profile['scope']} {profile['limitations']}".strip()
    if len(description) > 2000:
        raise SystemExit(
            f"Library description exceeds the catalog limit ({len(description)} characters); "
            "shorten the reviewed scope or limitations instead of truncating user-facing copy"
        )
    return description


def library_packages(
    admissions: dict[tuple[str, str], dict],
    starter: str,
    asset_revision: str,
    minimum_app_version: str,
) -> list[dict]:
    packages: list[dict] = []
    for entry in admissions.values():
        package_id, version = entry["id"], entry["version"]
        receipt_path = supply_evidence_path(entry["archive_receipt"])
        source_lock_path = supply_evidence_path(entry["source_lock"])
        validate_archive_receipt(entry, receipt_path, source_lock_path)
        receipt = read_json(receipt_path)
        manifest = receipt.get("manifest")
        package = receipt.get("package")
        build = receipt.get("build")
        if not isinstance(manifest, dict) or not isinstance(package, dict):
            raise SystemExit(f"Archive receipt is incomplete: {receipt_path}")
        if package.get("sha256") != entry["archive_sha256"]:
            raise SystemExit(f"Archive receipt digest drifted from admission: {package_id}")
        for field in ADMISSION_EVIDENCE_PATHS:
            if not supply_evidence_path(entry[field]).is_file():
                raise SystemExit(f"Admission evidence is missing: {package_id} {field}")
        lock = read_json(source_lock_path)
        records = entry["library_profile"]["record_count"]
        if manifest.get("id") != package_id or manifest.get("version") != version:
            raise SystemExit(f"Archive receipt identity drifted from admission: {receipt_path}")
        packages.append(
            {
                "file_name": package["file"],
                "url": f"{ASSET_PREFIX}/{asset_revision}/packages/{package['file']}",
                "download_format": "blobs",
                "download_bytes": package["bytes"],
                "download_sha256": package["sha256"],
                "manifest": manifest,
                "role": "starter" if package_id == starter else "optional",
                "description": library_description(entry["library_profile"], records),
                "language": library_language(lock),
                "publisher": lock.get("publisher"),
                "library_profile": entry["library_profile"],
                "compatibility": {
                    "format": "Blobs library archive",
                    "corpus_schema": build["schema"],
                    "retrieval_format": "SQLite FTS5",
                    "minimum_app_version": minimum_app_version,
                },
            }
        )
    if not any(item["role"] == "starter" for item in packages):
        raise SystemExit(f"Starter library {starter!r} is not release-ready in the supply registry")
    return packages


def build_catalog(
    registry_path: Path,
    release_tag: str,
    asset_revision: str,
    starter: str,
    minimum_app_version: str,
    catalog_revision: int,
) -> dict:
    if not RELEASE_TAG.fullmatch(release_tag):
        raise SystemExit("Release tag must be a semantic version such as v0.4.0")
    if not ASSET_REVISION.fullmatch(asset_revision):
        raise SystemExit("Asset revision must be a 40-character Hugging Face commit hash")
    if type(catalog_revision) is not int or catalog_revision < 1:
        raise SystemExit("Catalog revision must be a positive integer")
    admissions = read_release_admissions(registry_path)
    if not admissions:
        raise SystemExit("The supply registry admits no libraries, so there is nothing to publish")
    catalog = {
        "format": 1,
        "revision": catalog_revision,
        "release_tag": release_tag,
        "packages": model_packages(minimum_app_version)
        + library_packages(admissions, starter, asset_revision, minimum_app_version),
    }
    validate_catalog(catalog)
    require_release_admissions(catalog, admissions)
    return catalog


def comparable(catalog: dict) -> dict:
    """Strip the fields a publisher rewrites at upload time, so drift means a real change."""
    packages = {}
    for item in catalog.get("packages", []):
        manifest = item.get("manifest", {})
        entry = {key: value for key, value in item.items() if key != "url"}
        entry["url"] = re.sub(
            r"/resolve/[a-f0-9]{40}/", "/resolve/{revision}/", item.get("url", "")
        )
        packages[f"{manifest.get('id')}@{manifest.get('version')}"] = entry
    return {"release_tag": catalog.get("release_tag"), "packages": packages}


def drift_report(committed: dict, generated: dict) -> list[str]:
    before, after = comparable(committed), comparable(generated)
    findings: list[str] = []
    for package_id in sorted(set(before["packages"]) - set(after["packages"])):
        findings.append(f"{package_id} is advertised but no longer admitted for release")
    for package_id in sorted(set(after["packages"]) - set(before["packages"])):
        findings.append(f"{package_id} is admitted for release but not advertised")
    for package_id in sorted(set(before["packages"]) & set(after["packages"])):
        old, new = before["packages"][package_id], after["packages"][package_id]
        for field in sorted(set(old) | set(new)):
            if old.get(field) != new.get(field):
                findings.append(f"{package_id} field {field} differs from the supply registry")
    if before["release_tag"] != after["release_tag"]:
        findings.append("release_tag differs from the requested release")
    return findings


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--registry", type=Path, default=REGISTRY)
    parser.add_argument("--release-tag", required=True)
    parser.add_argument(
        "--asset-revision",
        required=True,
        help="Immutable Hugging Face commit revision hosting packages/ at this catalog revision.",
    )
    parser.add_argument("--starter", default="wikivoyage-en")
    parser.add_argument("--minimum-app-version", default="0.3.1")
    parser.add_argument(
        "--revision",
        type=int,
        required=True,
        help="Catalog revision; a published revision must exceed every previous one.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        help="Write the catalog here. Omit to print it; use --check to compare the shipped file.",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Report how the shipped catalog drifts from the supply registry and exit nonzero.",
    )
    args = parser.parse_args()
    catalog = build_catalog(
        args.registry,
        args.release_tag,
        args.asset_revision,
        args.starter,
        args.minimum_app_version,
        args.revision,
    )
    if args.check:
        findings = drift_report(read_json(CATALOG), catalog)
        for finding in findings:
            print(f"DRIFT: {finding}")
        if findings:
            raise SystemExit(1)
        print("Shipped catalog matches the supply registry.")
        return
    text = json.dumps(catalog, indent=2, ensure_ascii=False) + "\n"
    if args.output:
        args.output.write_text(text, encoding="utf-8")
        libraries = sum(1 for item in catalog["packages"] if item["role"] != "model")
        print(f"Wrote {len(catalog['packages'])} packages ({libraries} libraries) to {args.output}")
    else:
        print(text, end="")


if __name__ == "__main__":
    main()
