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
from sign_catalog import sign_catalog, valid_library_profile, validate_catalog

ROOT = Path(__file__).resolve().parents[1]
REPO_ID = "nonomnonom/blobs-catalog"
ASSET_PREFIX = f"https://huggingface.co/datasets/{REPO_ID}/resolve"
SUPPLY_REGISTRY = ROOT.parent / "corpus" / "supply" / "registry.json"
SHA256 = re.compile(r"[a-f0-9]{64}")
ADMISSION_EVIDENCE_PATHS = (
    "source_lock",
    "rights_review",
    "reviewed_records",
    "retrieval_cases",
    "archive_receipt",
)


def sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def source_lock_sha256(path: Path) -> str:
    """Match the canonical source-lock identity recorded by corpus extraction."""
    try:
        lock = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ValueError(f"Source lock is invalid JSON: {path}") from error
    if not isinstance(lock, dict):
        raise ValueError(f"Source lock must be an object: {path}")
    return hashlib.sha256(json.dumps(lock, sort_keys=True).encode()).hexdigest()


def read_json_evidence(path: Path, label: str) -> object:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ValueError(f"{label} is invalid JSON: {path}") from error


LANE_ARCHIVE_IDENTITY = ("id", "edition", "filename", "bytes", "sha256")


def validate_lane_archives(lock: dict, records: dict, source_lock: Path) -> None:
    """Rebuild every merged archive identity from the lane lock and its member locks."""
    archives, recorded = lock.get("archives"), records.get("archives")
    if (
        not isinstance(archives, list)
        or not archives
        or not isinstance(recorded, list)
        or len(archives) != len(recorded)
    ):
        raise ValueError("Lane source lock and reviewed records disagree on archives")
    members = {}
    for item in recorded:
        if not isinstance(item, dict) or not isinstance(item.get("id"), str):
            raise ValueError("Reviewed records list malformed lane archives")
        if item["id"] in members:
            raise ValueError("Reviewed records repeat a lane archive")
        members[item["id"]] = item
    for entry in archives:
        if not isinstance(entry, dict) or entry.get("id") not in members:
            raise ValueError("Lane source lock names an archive the records do not retain")
        member = members.pop(entry["id"])
        if any(entry.get(field) != member.get(field) for field in LANE_ARCHIVE_IDENTITY):
            raise ValueError(f"Lane source lock differs from records for {entry['id']}")
        if entry.get("manifest_sha256") != member.get("lock_sha256"):
            raise ValueError(f"Lane archive manifest digest differs for {entry['id']}")
        relative = entry.get("lock")
        if not isinstance(relative, str) or not relative or Path(relative).is_absolute():
            raise ValueError(f"Lane archive lock path is invalid for {entry['id']}")
        member_lock = (source_lock.parent / relative).resolve()
        if member_lock.parent != source_lock.parent.resolve() or not member_lock.is_file():
            raise ValueError(f"Lane archive lock is missing for {entry['id']}")
        admitted = read_json_evidence(member_lock, "Archive source lock")
        if (
            not isinstance(admitted, dict)
            or any(admitted.get(field) != member.get(field) for field in LANE_ARCHIVE_IDENTITY)
            or entry.get("lock_sha256") != source_lock_sha256(member_lock)
        ):
            raise ValueError(f"Lane archive lock does not identify {entry['id']}")


def validate_source_and_records(source_lock: Path, reviewed_records: Path) -> None:
    """Require one reviewed record manifest to identify the admitted source lock."""
    lock = read_json_evidence(source_lock, "Source lock")
    records = read_json_evidence(reviewed_records, "Reviewed records manifest")
    if not isinstance(lock, dict) or not isinstance(records, dict):
        raise ValueError("Source lock and reviewed records manifest must be JSON objects")
    source_id, edition = lock.get("id"), lock.get("edition")
    if (
        not isinstance(source_id, str)
        or not re.fullmatch(r"[a-z0-9-]{1,64}", source_id)
        or not isinstance(edition, str)
        or not re.fullmatch(r"[A-Za-z0-9.-]{1,40}", edition)
        or records.get("format") != 1
        or records.get("complete") is not True
        or not isinstance(records.get("curation"), dict)
        or not isinstance(records.get("source"), dict)
        or records["source"].get("id") != source_id
        or records["source"].get("edition") != edition
        or not isinstance(records.get("identity"), dict)
    ):
        raise ValueError(
            "Reviewed records do not identify the complete, curated admitted source lock"
        )
    if "archives" in lock:
        validate_lane_archives(lock, records, source_lock)
    elif records["identity"].get("lock_sha256") != source_lock_sha256(source_lock):
        raise ValueError(
            "Reviewed records do not identify the complete, curated admitted source lock"
        )


def validate_retrieval_cases(entry: dict, path: Path) -> None:
    """Bind a release to non-empty, versioned lexical hit and miss cases."""
    case_set = read_json_evidence(path, "Retrieval case set")
    if not isinstance(case_set, dict) or case_set.get("format") != 1:
        raise ValueError(f"Retrieval case set has an unsupported format: {path}")
    library = case_set.get("library")
    if (
        not isinstance(library, dict)
        or library.get("id") != entry["id"]
        or library.get("version") != entry["version"]
        or library.get("records_sha256") != entry["records_sha256"]
    ):
        raise ValueError(f"Retrieval case set has a different library identity: {path}")
    cases = case_set.get("cases")
    if not isinstance(cases, list) or not 1 <= len(cases) <= 10_000:
        raise ValueError(f"Retrieval case set must contain 1 to 10,000 cases: {path}")
    ids = set()
    for case in cases:
        if not isinstance(case, dict):
            raise ValueError(f"Retrieval case must be an object: {path}")
        case_id, question, expectation = (
            case.get("id"),
            case.get("question"),
            case.get("expectation"),
        )
        if (
            not isinstance(case_id, str)
            or not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,63}", case_id)
            or case_id in ids
            or not isinstance(question, str)
            or not question.strip()
            or len(question) > 1_000
            or "\x00" in question
            or "\ufffd" in question
            or expectation not in {"hit", "miss"}
        ):
            raise ValueError(f"Retrieval case is invalid: {path}")
        ids.add(case_id)
        document_ids = case.get("expected_document_ids", [])
        if not isinstance(document_ids, list) or len(document_ids) > 16 or any(
            not isinstance(document_id, str)
            or not re.fullmatch(r"[a-zA-Z0-9_-]{1,100}", document_id)
            for document_id in document_ids
        ):
            raise ValueError(f"Retrieval case has invalid document IDs: {path}")
        if (expectation == "hit") != bool(document_ids):
            raise ValueError(f"Retrieval case expectation does not match document IDs: {path}")


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
    if item.get("download_format") != "blobs":
        return
    manifest = verify_package(path)
    if manifest != item.get("manifest"):
        raise ValueError(f"Package manifest differs from the download list: {path.name}")
    if manifest["kind"] == "library" and not re.fullmatch(
        r"[a-f0-9]{64}", manifest.get("records_sha256", "")
    ):
        raise ValueError(
            f"New library package lacks a normalized records SHA-256: {path.name}"
        )


def read_release_admissions(path: Path = SUPPLY_REGISTRY) -> dict[tuple[str, str], dict]:
    """Return the exact library editions that data supply has admitted for release."""
    try:
        registry = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as error:
        raise ValueError(f"Knowledge supply registry is missing: {path}") from error
    except json.JSONDecodeError as error:
        raise ValueError(f"Knowledge supply registry is invalid JSON: {path}") from error
    if not isinstance(registry, dict) or registry.get("format") != 1:
        raise ValueError("Knowledge supply registry format is not supported")
    entries = registry.get("release_ready")
    if not isinstance(entries, list):
        raise ValueError("Knowledge supply registry must declare release_ready")

    admissions: dict[tuple[str, str], dict] = {}
    for entry in entries:
        if not isinstance(entry, dict):
            raise ValueError("Knowledge supply release admission must be an object")
        package_id, version = entry.get("id"), entry.get("version")
        if (
            not isinstance(package_id, str)
            or not re.fullmatch(r"[a-z0-9-]{1,64}", package_id)
            or not isinstance(version, str)
            or not re.fullmatch(r"[A-Za-z0-9.-]{1,40}", version)
        ):
            raise ValueError("Knowledge supply release admission has an invalid identity")
        key = (package_id, version)
        if key in admissions:
            raise ValueError(f"Knowledge supply repeats release admission: {package_id} {version}")
        for field in ADMISSION_EVIDENCE_PATHS:
            value = entry.get(field)
            if not isinstance(value, str) or not value or Path(value).is_absolute():
                raise ValueError(f"Knowledge supply {package_id} {version} has invalid {field}")
        for field in ("records_sha256", "archive_sha256"):
            if not isinstance(entry.get(field), str) or not SHA256.fullmatch(entry[field]):
                raise ValueError(f"Knowledge supply {package_id} {version} has invalid {field}")
        profile = entry.get("library_profile")
        if not valid_library_profile(profile):
            raise ValueError(
                f"Knowledge supply {package_id} {version} has an invalid library profile"
            )
        partition_plan, partition_id = entry.get("partition_plan"), entry.get("partition_id")
        if (partition_plan is None) != (partition_id is None):
            raise ValueError(
                f"Knowledge supply {package_id} {version} must declare both partition fields"
            )
        if partition_plan is not None and (
            not isinstance(partition_plan, str)
            or not partition_plan
            or Path(partition_plan).is_absolute()
            or not isinstance(partition_id, str)
            or not re.fullmatch(r"p\d{3}", partition_id)
        ):
            raise ValueError(f"Knowledge supply {package_id} {version} has an invalid partition")
        admissions[key] = entry
    return admissions


def supply_evidence_path(relative_path: str) -> Path:
    """Resolve a registry path only when it stays inside the repository root."""
    repository = ROOT.parent.resolve()
    candidate = (repository / relative_path).resolve()
    try:
        candidate.relative_to(repository)
    except ValueError as error:
        raise ValueError(f"Knowledge supply evidence escapes the repository: {relative_path}") from error
    return candidate


def validate_archive_receipt(entry: dict, receipt_path: Path, source_lock: Path) -> None:
    """Bind source-admission identities to the receipt made beside the archive."""
    try:
        receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ValueError(f"Archive receipt is invalid JSON: {receipt_path}") from error
    if not isinstance(receipt, dict):
        raise ValueError(f"Archive receipt must be an object: {receipt_path}")
    manifest = receipt.get("manifest")
    package = receipt.get("package")
    if not isinstance(manifest, dict) or not isinstance(package, dict):
        raise ValueError(f"Archive receipt lacks manifest or package metadata: {receipt_path}")
    expected_manifest = {
        "id": entry["id"],
        "version": entry["version"],
        "kind": "library",
        "records_sha256": entry["records_sha256"],
    }
    if any(manifest.get(field) != value for field, value in expected_manifest.items()):
        raise ValueError(f"Archive receipt manifest differs from supply admission: {receipt_path}")
    if package.get("sha256") != entry["archive_sha256"]:
        raise ValueError(f"Archive receipt digest differs from supply admission: {receipt_path}")
    build = receipt.get("build")
    if (
        not isinstance(build, dict)
        or type(build.get("documents")) is not int
        or build["documents"] != entry["library_profile"]["record_count"]
        or build.get("records_sha256") != entry["records_sha256"]
    ):
        raise ValueError(f"Archive receipt build metadata differs from supply admission: {receipt_path}")
    partition_plan = entry.get("partition_plan")
    if partition_plan is None:
        return
    plan_path = supply_evidence_path(partition_plan)
    try:
        plan = json.loads(plan_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ValueError(f"Partition plan is invalid JSON: {plan_path}") from error
    lock_identity_sha256 = source_lock_sha256(source_lock)
    if (
        not isinstance(plan, dict)
        or plan.get("format") != 1
        or plan.get("algorithm") != "sha256-document-id-modulo-v1"
        or plan.get("source_lock_sha256") != lock_identity_sha256
        or not any(
            isinstance(partition, dict) and partition.get("id") == entry["partition_id"]
            for partition in plan.get("partitions", [])
        )
    ):
        raise ValueError(f"Partition plan does not match supply admission: {plan_path}")
    partition = build.get("partition") if isinstance(build, dict) else None
    if (
        not isinstance(partition, dict)
        or partition.get("id") != entry["partition_id"]
        or partition.get("plan_sha256") != sha256(plan_path)
        or partition.get("source_lock_sha256") != lock_identity_sha256
    ):
        raise ValueError(f"Archive receipt lacks the admitted partition identity: {receipt_path}")


def require_release_admissions(catalog: dict, admissions: dict[tuple[str, str], dict]) -> None:
    """Bind every catalogued library to its reviewed source, records, and archive."""
    for item in catalog.get("packages", []):
        manifest = item.get("manifest", {})
        if manifest.get("kind") != "library":
            continue
        package_id, version = manifest.get("id"), manifest.get("version")
        entry = admissions.get((package_id, version))
        if entry is None:
            raise ValueError(
                "Library is not release-ready in corpus/supply/registry.json: "
                f"{package_id} {version}"
            )
        if manifest.get("records_sha256") != entry["records_sha256"]:
            raise ValueError(
                f"Library record digest differs from its supply admission: {package_id} {version}"
            )
        if item.get("download_sha256") != entry["archive_sha256"]:
            raise ValueError(
                f"Library archive digest differs from its supply admission: {package_id} {version}"
            )
        if item.get("library_profile") != entry["library_profile"]:
            raise ValueError(
                f"Library profile differs from its supply admission: {package_id} {version}"
            )
        evidence = {
            field: supply_evidence_path(entry[field]) for field in ADMISSION_EVIDENCE_PATHS
        }
        partition_plan = entry.get("partition_plan")
        if partition_plan is not None:
            evidence["partition_plan"] = supply_evidence_path(partition_plan)
        if any(not path.is_file() or path.stat().st_size == 0 for path in evidence.values()):
            raise ValueError(
                "Library supply evidence is missing from this release checkout: "
                f"{package_id} {version}"
            )
        validate_source_and_records(evidence["source_lock"], evidence["reviewed_records"])
        validate_retrieval_cases(entry, evidence["retrieval_cases"])
        validate_archive_receipt(entry, evidence["archive_receipt"], evidence["source_lock"])


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
    require_release_admissions(catalog, read_release_admissions())
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
