"""Verify a Blobs offline release kit after copying or downloading it."""

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import zipfile

from build_package import verify_package
from blobs_corpus.footprint import (
    STORAGE_LIMIT_BYTES,
    catalog_storage_values,
    installer_policy_text,
    package_payload_limits_text,
    storage_values,
)


CHECKSUMS = "SHA256SUMS.txt"
CHECKSUM_LINE = re.compile(r"([0-9a-f]{64})  ([^\r\n]+)")
VERSION = re.compile(r"[0-9A-Za-z][0-9A-Za-z.+-]{0,63}")
SOURCE_REQUIRED = {
    "blobs/.gitattributes",
    "blobs/README.md",
    "blobs/android/app/src/main/assets/package-catalog.json",
    "blobs/android/app/src/main/kotlin/dev/blobs/app/data/packages/PackageCatalog.kt",
    "blobs/android/app/src/main/kotlin/dev/blobs/app/data/packages/PackageDownloader.kt",
    "blobs/android/app/src/main/kotlin/dev/blobs/app/data/packages/PackageInstaller.kt",
    "blobs/android/tools/package_release.py",
    "blobs/android/tools/verify_release.py",
    "blobs/corpus/README.md",
    "blobs/corpus/pyproject.toml",
    "blobs/corpus/uv.lock",
}
SOURCE_FORBIDDEN_PREFIXES = (
    "blobs/design/",
    "blobs/android/.local/",
    "blobs/android/packages/",
    "blobs/android/release/",
    "blobs/corpus/artifacts/",
    "blobs/corpus/reports/",
    "blobs/corpus/work/",
)
SOURCE_FORBIDDEN_SUFFIXES = (".gguf", ".jks", ".keystore")


def digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def catalog_edition(catalog: dict) -> str:
    tag = catalog.get("release_tag", "")
    if (
        catalog.get("format") != 1
        or not isinstance(tag, str)
        or not re.fullmatch(r"v[0-9]+\.[0-9]+\.[0-9]+", tag)
    ):
        raise ValueError("Invalid pinned download catalog edition")
    return tag


def read_json(path: Path):
    def reject_duplicates(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f"Duplicate JSON key in {path.name}: {key}")
            result[key] = value
        return result

    try:
        value = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=reject_duplicates)
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError(f"Invalid JSON: {path.name}") from error
    if not isinstance(value, dict):
        raise ValueError(f"Expected a JSON object: {path.name}")
    return value


def safe_relative(name: str) -> PurePosixPath:
    if "\\" in name:
        raise ValueError(f"Backslashes are not allowed in release paths: {name}")
    relative = PurePosixPath(name)
    if (
        relative.is_absolute()
        or not relative.parts
        or any(part in {"", ".", ".."} for part in relative.parts)
    ):
        raise ValueError(f"Unsafe release path: {name}")
    return relative


def files_in(root: Path) -> dict[str, Path]:
    files = {}
    folded = {}
    for path in root.rglob("*"):
        if path.is_symlink():
            raise ValueError(
                f"Release kits cannot contain symbolic links: {path.relative_to(root)}"
            )
        if not path.is_file():
            continue
        relative = path.relative_to(root).as_posix()
        key = relative.casefold()
        if key in folded:
            raise ValueError(f"Case-insensitive path collision: {folded[key]} and {relative}")
        folded[key] = relative
        files[relative] = path
    return files


def expected_checksums(path: Path) -> dict[str, str]:
    if path.stat().st_size > 4 * 1024 * 1024:
        raise ValueError("Checksum inventory is unexpectedly large")
    expected = {}
    folded = {}
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        match = CHECKSUM_LINE.fullmatch(line)
        if match is None:
            raise ValueError(f"Invalid checksum line {number}")
        checksum, name = match.groups()
        relative = safe_relative(name).as_posix()
        if relative == CHECKSUMS:
            raise ValueError("The checksum inventory cannot contain itself")
        key = relative.casefold()
        if key in folded:
            raise ValueError(f"Duplicate checksum path: {folded[key]} and {relative}")
        folded[key] = relative
        expected[relative] = checksum
    if not expected:
        raise ValueError("Checksum inventory is empty")
    return expected


def verify_source_archive(path: Path) -> int:
    try:
        with zipfile.ZipFile(path) as archive:
            names = []
            folded = set()
            for item in archive.infolist():
                if item.is_dir():
                    continue
                name = safe_relative(item.filename).as_posix()
                if (item.external_attr >> 16) & 0o170000 == 0o120000:
                    raise ValueError(f"Source archive contains a symbolic link: {name}")
                key = name.casefold()
                if key in folded:
                    raise ValueError(f"Source archive path collision: {name}")
                folded.add(key)
                names.append(name)
    except zipfile.BadZipFile as error:
        raise ValueError("Invalid source archive") from error
    missing = SOURCE_REQUIRED - set(names)
    if missing:
        raise ValueError(f"Source archive is missing required files: {sorted(missing)}")
    forbidden = [
        name
        for name in names
        if name.startswith(SOURCE_FORBIDDEN_PREFIXES)
        or name.casefold().endswith(SOURCE_FORBIDDEN_SUFFIXES)
    ]
    if forbidden:
        raise ValueError(f"Source archive contains generated or private artifacts: {forbidden[0]}")
    return len(names)


def verify_kit(directory: Path) -> dict:
    root = directory.resolve(strict=True)
    if not root.is_dir():
        raise ValueError("Release kit must be a directory")
    files = files_in(root)
    checksum_path = files.get(CHECKSUMS)
    if checksum_path is None:
        raise ValueError(f"Missing {CHECKSUMS}")
    expected = expected_checksums(checksum_path)
    actual = set(files) - {CHECKSUMS}
    if actual != set(expected):
        missing = sorted(set(expected) - actual)
        extra = sorted(actual - set(expected))
        raise ValueError(f"Release inventory differs; missing={missing}, extra={extra}")
    for name, checksum in expected.items():
        if digest(files[name]) != checksum:
            raise ValueError(f"Checksum mismatch: {name}")

    required = {
        "APK-VERIFICATION.json",
        "FOOTPRINT.json",
        "INSTALL.md",
        "PACKAGES.json",
        "PREPARATION.md",
        "SIGNATURE.txt",
        "THIRD_PARTY_NOTICES.md",
        "VERIFY.md",
    }
    missing = required - actual
    if missing:
        raise ValueError(f"Release kit is missing required files: {sorted(missing)}")
    if not any(name.startswith("licenses/") for name in actual):
        raise ValueError("Release kit does not contain offline license texts")
    if not files["SIGNATURE.txt"].read_text(encoding="utf-8").strip():
        raise ValueError("Signature report is empty")

    apk_report = read_json(files["APK-VERIFICATION.json"])
    if apk_report.get("application_id") != "dev.blobs.app":
        raise ValueError("APK report does not identify the production application")
    version = apk_report.get("version_name")
    if not isinstance(version, str) or VERSION.fullmatch(version) is None:
        raise ValueError("APK report has an invalid version")
    apk_name = apk_report.get("apk")
    if apk_name != f"blobs-{version}.apk" or apk_name not in actual:
        raise ValueError("APK filename does not match the verified release version")
    apk_path = files[apk_name]
    if type(apk_report.get("bytes")) is not int or apk_report["bytes"] != apk_path.stat().st_size:
        raise ValueError("APK byte count differs from its report")
    if apk_report.get("sha256") != digest(apk_path):
        raise ValueError("APK hash differs from its report")
    if (
        apk_report.get("internet_permission") is not True
        or apk_report.get("zip_alignment_16kb") is not True
    ):
        raise ValueError(
            "APK report does not prove package-download permission and 16 KB packaging"
        )
    libraries = apk_report.get("native_libraries")
    if not isinstance(libraries, list):
        raise ValueError("APK report does not list native libraries")
    try:
        abis = sorted({item["file"].split("/")[1] for item in libraries})
    except (KeyError, IndexError, TypeError, AttributeError) as error:
        raise ValueError("APK report has an invalid native-library inventory") from error
    if abis != ["arm64-v8a", "x86_64"]:
        raise ValueError(f"APK report has unexpected ABIs: {abis}")

    recorded_packages = read_json(files["PACKAGES.json"])
    package_names = sorted(name for name in actual if name.endswith(".blobs"))
    if len(package_names) != 2 or set(recorded_packages) != set(package_names):
        raise ValueError("Expected exactly the two packages recorded in PACKAGES.json")
    kinds = set()
    packages = []
    for name in package_names:
        if safe_relative(name).parent != PurePosixPath("."):
            raise ValueError("Packages must be top-level release files")
        manifest = verify_package(files[name])
        if manifest != recorded_packages[name]:
            raise ValueError(f"Package manifest differs from PACKAGES.json: {name}")
        kinds.add(manifest["kind"])
        packages.append(
            {
                "file": name,
                "kind": manifest["kind"],
                "id": manifest["id"],
                "version": manifest["version"],
                "payload_bytes": manifest["bytes"],
                "payload_sha256": manifest["sha256"],
                "package_bytes": files[name].stat().st_size,
                "package_sha256": expected[name],
            }
        )
    if kinds != {"library", "model"}:
        raise ValueError("Release kit must contain one model and one library package")

    source_name = f"blobs-{version}-source.zip"
    source_archives = sorted(name for name in actual if name.endswith("-source.zip"))
    if source_archives != [source_name]:
        raise ValueError("Source archive does not match the verified release version")
    source_files = verify_source_archive(files[source_name])
    with zipfile.ZipFile(files[source_name]) as archive:
        try:
            catalog = json.loads(
                archive.read("blobs/android/app/src/main/assets/package-catalog.json")
            )
        except (KeyError, UnicodeDecodeError, json.JSONDecodeError) as error:
            raise ValueError("Source archive has no valid in-app package catalog") from error
    asset_edition = catalog_edition(catalog)
    catalog_packages = {
        item.get("file_name"): item
        for item in catalog.get("packages", [])
        if item.get("download_format") == "blobs"
    }
    if not catalog_packages:
        raise ValueError("In-app catalog must contain one model and one library")
    for package in packages:
        name = package["file"]
        item = catalog_packages.get(name)
        url = item.get("url", "") if item else ""
        github_release_url = (
            f"https://github.com/nonomnonom/blobs/releases/download/{asset_edition}/{name}"
        )
        hf_package_url = re.fullmatch(
            rf"https://huggingface\.co/datasets/nonomnonom/blobs-catalog/resolve/[a-f0-9]{{40}}/packages/{re.escape(name)}",
            url,
        )
        if (
            item is None
            or item.get("manifest", {}).get("kind") != package["kind"]
            or item.get("download_bytes") != package["package_bytes"]
            or item.get("download_sha256") != package["package_sha256"]
            or (url != github_release_url and hf_package_url is None)
            or item.get("manifest") != recorded_packages[name]
        ):
            raise ValueError(f"In-app catalog differs from the delivered {package['kind']} package")

    footprint = read_json(files["FOOTPRINT.json"])
    footprint_format = footprint.get("format")
    if footprint_format not in {1, 2} or footprint.get("complete") is not True:
        raise ValueError("FOOTPRINT.json is incomplete or unsupported")

    policy_path = "android/app/src/main/kotlin/dev/blobs/app/data/packages/PackageInstaller.kt"
    package_policy_path = (
        "android/app/src/main/kotlin/dev/blobs/app/data/packages/PackageManifest.kt"
    )
    with zipfile.ZipFile(files[source_name]) as archive:
        policy_source = archive.read(f"blobs/{policy_path}")
        catalog_source = archive.read("blobs/android/app/src/main/assets/package-catalog.json")
        if footprint_format == 2:
            try:
                package_policy_source = archive.read(f"blobs/{package_policy_path}")
            except KeyError as error:
                raise ValueError("Source archive is missing the package payload policy") from error

    policy = installer_policy_text(
        policy_source.decode("utf-8"), hashlib.sha256(policy_source).hexdigest()
    )
    if footprint_format == 2:
        policy["package_payload_limits"] = package_payload_limits_text(
            package_policy_source.decode("utf-8"), hashlib.sha256(package_policy_source).hexdigest()
        )
    expected_policy = dict(file=policy_path, **policy)
    if footprint_format == 2:
        expected_policy["package_payload_limits"]["source_file"] = package_policy_path
    if footprint.get("apk_report") != dict(
        file="APK-VERIFICATION.json",
        sha256=digest(files["APK-VERIFICATION.json"]),
        value=apk_report,
    ):
        raise ValueError("FOOTPRINT.json does not identify this APK report")
    if footprint.get("installer_policy") != expected_policy:
        raise ValueError("FOOTPRINT.json does not match the delivered installer policy")
    import_order = footprint.get("import_order")
    if (
        not isinstance(import_order, list)
        or len(import_order) != len(package_names)
        or len(set(import_order)) != len(import_order)
        or set(import_order) != set(package_names)
    ):
        raise ValueError("FOOTPRINT.json has an invalid package import order")
    by_name = {item["file"]: item for item in packages}
    ordered_packages = [
        dict(
            manifest={"bytes": by_name[name]["payload_bytes"]},
            package={"file": name, "bytes": by_name[name]["package_bytes"]},
        )
        for name in import_order
    ]
    expected_storage = storage_values(
        ordered_packages, apk_path.stat().st_size, policy["import_headroom_bytes"]
    )
    if footprint.get("storage") != expected_storage:
        raise ValueError("FOOTPRINT.json storage values differ from delivered APK and packages")
    expected_catalog_storage = None
    if footprint_format == 2:
        expected_catalog_storage = catalog_storage_values(catalog_source, apk_path.stat().st_size)
        expected_catalog_storage["source_file"] = (
            "blobs/android/app/src/main/assets/package-catalog.json"
        )
        if footprint.get("catalog_storage") != expected_catalog_storage:
            raise ValueError("FOOTPRINT.json does not account for all in-app catalog assets")
    checks = footprint.get("checks", {})
    steady = apk_path.stat().st_size + expected_storage["installed_payload_bytes"]
    if (
        checks.get("storage_limit", {}).get("limit_bytes") != STORAGE_LIMIT_BYTES
        or checks.get("storage_limit", {}).get("known_steady_bytes") != steady
        or checks.get("storage_limit", {}).get("passes") is not (steady <= STORAGE_LIMIT_BYTES)
        or checks.get("android_installed_payload_cap", {}).get("selected_bytes")
        != expected_storage["installed_payload_bytes"]
        or checks.get("android_installed_payload_cap", {}).get("limit_bytes")
        != policy["installed_payload_cap_bytes"]
        or checks.get("android_installed_payload_cap", {}).get("passes")
        is not (expected_storage["installed_payload_bytes"] < policy["installed_payload_cap_bytes"])
    ):
        raise ValueError("FOOTPRINT.json does not prove the storage limits for this edition")

    install = files["INSTALL.md"].read_text(encoding="utf-8")
    for name in [apk_name, *package_names]:
        if f"`{name}`" not in install:
            raise ValueError(f"INSTALL.md does not name delivered file: {name}")

    result = {
        "format": 1,
        "complete": True,
        "version": version,
        "application_id": apk_report["application_id"],
        "apk": {
            "file": apk_name,
            "bytes": apk_path.stat().st_size,
            "sha256": apk_report["sha256"],
            "abis": abis,
            "internet_permission": True,
            "zip_alignment_16kb": True,
        },
        "packages": packages,
        "source_archive": {
            "file": source_name,
            "files": source_files,
            "bytes": files[source_name].stat().st_size,
            "sha256": expected[source_name],
        },
        "footprint": {
            "distribution_bytes": expected_storage["distribution_bytes"],
            "installed_payload_bytes": expected_storage["installed_payload_bytes"],
            "known_steady_bytes": steady,
            "storage_limit_bytes": STORAGE_LIMIT_BYTES,
            "storage_limit_passes": checks["storage_limit"]["passes"],
            "peak_retained_import_bytes": expected_storage["retained_sources"]["peak_bytes"],
        },
        "checksums": {
            "file": CHECKSUMS,
            "entries": len(expected),
            "sha256": digest(checksum_path),
        },
        "kit_files": len(files),
        "kit_bytes": sum(path.stat().st_size for path in files.values()),
        "scope": "Transport integrity, package payloads, recorded APK gates and source-kit composition; rerun verify_apk.py and apksigner for an independent APK inspection.",
    }
    if expected_catalog_storage is not None:
        result["footprint"].update(
            all_catalog_installed_payload_bytes=expected_catalog_storage["all_catalog_options"][
                "installed_payload_bytes"
            ],
            all_catalog_known_steady_bytes=expected_catalog_storage["all_catalog_options"][
                "known_steady_bytes"
            ],
            all_catalog_storage_limit_passes=expected_catalog_storage["all_catalog_options"][
                "storage_limit_passes"
            ],
            all_catalog_peak_retained_download_bytes=expected_catalog_storage[
                "all_catalog_options"
            ]["peak_if_every_download_is_retained_bytes"],
        )
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    try:
        result = verify_kit(args.directory)
    except (OSError, UnicodeError, ValueError, zipfile.BadZipFile) as error:
        parser.exit(1, f"FAIL: {error}\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
