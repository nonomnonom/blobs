"""Verify an offline kit and calculate its storage footprint without copying artifacts."""

import argparse
import hashlib
import json
from pathlib import Path
import re

from .extract import write_json
from .package import verify_package


STORAGE_LIMIT_BYTES = 50_000_000_000
MAX_MODEL_PAYLOAD_BYTES = 40_000_000_000
MAX_LIBRARY_PAYLOAD_BYTES = 6 * 1024**3


def sha(path: Path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def read_installer_policy(path: Path):
    source = path.read_bytes()
    text = source.decode("utf-8")
    return installer_policy_text(text, hashlib.sha256(source).hexdigest())


def installer_policy_text(text: str, source_sha256: str):
    headroom = re.findall(r"expected\.bytes - resumeBytes \+ (\d+)L \* 1024 \* 1024", text)
    caps = re.findall(
        r"(?:private|internal) const val MAX_INSTALLED_PAYLOAD_BYTES = ([\d_]+)L", text
    )
    if not caps:
        # Keep prior release-evidence fixtures readable. Current Android sources use the
        # named constant above so the bound cannot be mistaken for a per-library limit.
        caps = re.findall(
            r"dao\.all\(\)\.sumOf \{ it\.bytes \} \+ expected\.bytes < ([\d_]+)L",
            text,
        )
    if headroom != ["64"] or caps != ["48_000_000_000"]:
        raise ValueError("Android import storage policy changed; review this calculator")
    return dict(
        import_headroom_bytes=int(headroom[0]) * 1024 * 1024,
        installed_payload_cap_bytes=int(caps[0].replace("_", "")),
        source_sha256=source_sha256,
    )


def read_package_payload_limits(path: Path):
    source = path.read_bytes()
    text = source.decode("utf-8")
    return package_payload_limits_text(text, hashlib.sha256(source).hexdigest())


def package_payload_limits_text(text: str, source_sha256: str):
    model = re.findall(r"internal const val MAX_MODEL_PAYLOAD_BYTES = ([\d_]+)L", text)
    library = re.findall(
        r"internal const val MAX_LIBRARY_PAYLOAD_BYTES = "
        r"([\d_]+)L \* ([\d_]+)L \* ([\d_]+)L \* ([\d_]+)L",
        text,
    )
    has_kind_mapping = (
        '"model" -> MAX_MODEL_PAYLOAD_BYTES' in text
        and '"library" -> MAX_LIBRARY_PAYLOAD_BYTES' in text
    )
    if model != ["40_000_000_000"] or len(library) != 1 or not has_kind_mapping:
        raise ValueError("Android package payload policy changed; review this calculator")
    library_bytes = 1
    for factor in library[0]:
        library_bytes *= int(factor.replace("_", ""))
    if library_bytes != 6 * 1024**3:
        raise ValueError("Android library payload limit changed; review this calculator")
    return dict(
        model_payload_cap_bytes=int(model[0].replace("_", "")),
        library_payload_cap_bytes=library_bytes,
        source_sha256=source_sha256,
    )


def read_apk_report(path: Path):
    report = json.loads(path.read_text(encoding="utf-8"))
    required = {
        "apk",
        "application_id",
        "version_code",
        "version_name",
        "bytes",
        "sha256",
        "internet_permission",
        "zip_alignment_16kb",
    }
    if not isinstance(report, dict) or not required <= report.keys():
        raise ValueError("Incomplete APK verification report")
    if (
        type(report["bytes"]) is not int
        or report["bytes"] <= 0
        or not re.fullmatch(r"[a-f0-9]{64}", report["sha256"])
        or type(report["version_code"]) is not int
        or report["version_code"] <= 0
        or not isinstance(report["version_name"], str)
        or not report["version_name"]
        or report["internet_permission"] is not True
        or report["zip_alignment_16kb"] is not True
    ):
        raise ValueError("APK report does not describe a package-download-enabled aligned build")
    return report


def storage_values(packages, apk_bytes, import_headroom):
    incoming = sum(item["package"]["bytes"] for item in packages)
    installed = sum(item["manifest"]["bytes"] for item in packages)
    cumulative = 0
    retained_steps = []
    move_steps = []
    for item in packages:
        payload = item["manifest"]["bytes"]
        package_bytes = item["package"]["bytes"]
        retained_steps.append(
            dict(
                package=item["package"]["file"],
                bytes=apk_bytes + incoming + cumulative + payload,
                composition="APK archive + all incoming packages + earlier installed payloads + current staging payload",
            )
        )
        move_steps.append(
            dict(
                package=item["package"]["file"],
                bytes=apk_bytes + cumulative + package_bytes + payload,
                composition="APK archive + earlier installed payloads + current incoming package + current staging payload",
            )
        )
        cumulative += payload
    return dict(
        distribution_bytes=apk_bytes + incoming,
        incoming_package_bytes=incoming,
        installed_payload_bytes=installed,
        known_steady_bytes=apk_bytes + installed,
        retained_sources=dict(
            peak_bytes=max(step["bytes"] for step in retained_steps), steps=retained_steps
        ),
        move_each_source_after_import=dict(
            peak_bytes=max(step["bytes"] for step in move_steps), steps=move_steps
        ),
        importer_allocatable_thresholds=[
            dict(
                package=item["package"]["file"],
                fresh_import_threshold_bytes=item["manifest"]["bytes"] + import_headroom,
                comparison="device allocatable bytes > threshold",
            )
            for item in packages
        ],
    )


def catalog_storage_values(
    source: bytes,
    apk_bytes: int,
    payload_limits=None,
    installed_payload_cap_bytes=48_000_000_000,
):
    """Bound storage for every model and library offered by the installed catalog."""
    try:
        catalog = json.loads(source.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError("Invalid package catalog for storage accounting") from error
    if not isinstance(catalog, dict) or catalog.get("format") != 1:
        raise ValueError("Unsupported package catalog for storage accounting")
    assets = catalog.get("packages")
    if not isinstance(assets, list) or not assets:
        raise ValueError("Package catalog has no assets")
    payload_limits = payload_limits or dict(
        model_payload_cap_bytes=MAX_MODEL_PAYLOAD_BYTES,
        library_payload_cap_bytes=MAX_LIBRARY_PAYLOAD_BYTES,
    )
    if (
        type(payload_limits.get("model_payload_cap_bytes")) is not int
        or type(payload_limits.get("library_payload_cap_bytes")) is not int
        or payload_limits["model_payload_cap_bytes"] <= 0
        or payload_limits["library_payload_cap_bytes"] <= 0
        or type(installed_payload_cap_bytes) is not int
        or installed_payload_cap_bytes <= 0
    ):
        raise ValueError("Invalid package payload limits")
    rows = []
    names = set()
    model_count = library_count = 0
    for item in assets:
        if not isinstance(item, dict):
            raise ValueError("Invalid package catalog entry")
        name = item.get("file_name")
        manifest = item.get("manifest")
        download_format = item.get("download_format")
        download_bytes = item.get("download_bytes")
        download_sha256 = item.get("download_sha256")
        manifest_hash = manifest.get("sha256") if isinstance(manifest, dict) else None
        if (
            not isinstance(name, str)
            or not name
            or name.casefold() in names
            or download_format not in {"blobs", "gguf"}
            or type(download_bytes) is not int
            or download_bytes <= 0
            or not isinstance(download_sha256, str)
            or not re.fullmatch(r"[a-f0-9]{64}", download_sha256)
            or not isinstance(manifest, dict)
            or type(manifest.get("bytes")) is not int
            or manifest["bytes"] <= 0
            or not isinstance(manifest_hash, str)
            or not re.fullmatch(r"[a-f0-9]{64}", manifest_hash)
        ):
            raise ValueError(f"Invalid package catalog metadata: {name!r}")
        kind = manifest.get("kind")
        if kind == "model":
            maximum_payload_bytes = payload_limits["model_payload_cap_bytes"]
            model_count += 1
        elif kind == "library":
            maximum_payload_bytes = payload_limits["library_payload_cap_bytes"]
            library_count += 1
        else:
            raise ValueError(f"Unsupported package kind in catalog: {name}")
        if download_bytes > maximum_payload_bytes or manifest["bytes"] > maximum_payload_bytes:
            raise ValueError(f"Package exceeds its catalog size limit: {name}")
        if download_format == "gguf" and download_bytes != manifest["bytes"]:
            raise ValueError(f"Raw GGUF download and installed payload sizes differ: {name}")
        names.add(name.casefold())
        rows.append(
            dict(
                file=name,
                kind=manifest["kind"],
                role=item.get("role"),
                download_format=download_format,
                download_bytes=download_bytes,
                download_sha256=download_sha256,
                payload_bytes=manifest["bytes"],
                payload_sha256=manifest_hash,
            )
        )
    if model_count == 0 or library_count == 0:
        raise ValueError("Package catalog must offer models and libraries")

    download_bytes = sum(row["download_bytes"] for row in rows)
    payload_bytes = sum(row["payload_bytes"] for row in rows)
    cumulative_payload = 0
    sequential_steps = []
    for row in rows:
        sequential_steps.append(
            dict(
                file=row["file"],
                bytes=apk_bytes + cumulative_payload + row["download_bytes"] + row["payload_bytes"],
                composition=(
                    "APK archive + previously installed catalog payloads + current download "
                    "+ current staging payload"
                ),
            )
        )
        cumulative_payload += row["payload_bytes"]
    known_steady = apk_bytes + payload_bytes
    retained_sources_peak = apk_bytes + download_bytes + payload_bytes
    return dict(
        source_sha256=hashlib.sha256(source).hexdigest(),
        asset_count=len(rows),
        assets=rows,
        all_catalog_options=dict(
            per_package_payload_limits=dict(
                model_bytes=payload_limits["model_payload_cap_bytes"],
                library_bytes=payload_limits["library_payload_cap_bytes"],
            ),
            downloadable_bytes=download_bytes,
            installed_payload_bytes=payload_bytes,
            known_steady_bytes=known_steady,
            peak_sequential_import_bytes=max(step["bytes"] for step in sequential_steps),
            sequential_import_steps=sequential_steps,
            peak_if_every_download_is_retained_bytes=retained_sources_peak,
            storage_limit_bytes=STORAGE_LIMIT_BYTES,
            storage_limit_margin_bytes=STORAGE_LIMIT_BYTES - known_steady,
            storage_limit_passes=known_steady <= STORAGE_LIMIT_BYTES,
            installed_payload_cap_bytes=installed_payload_cap_bytes,
            installed_payload_margin_bytes=installed_payload_cap_bytes - payload_bytes,
            installed_payload_cap_passes=payload_bytes < installed_payload_cap_bytes,
            importer_payload_cap_passes=payload_bytes < installed_payload_cap_bytes,
        ),
    )


def calculate(
    apk_report_path: Path,
    package_paths,
    installer_source: Path,
    catalog_path: Path | None = None,
    package_manifest_source: Path | None = None,
    manifests: dict | None = None,
):
    apk = read_apk_report(apk_report_path)
    if len(package_paths) != 2 or len(set(path.resolve() for path in package_paths)) != 2:
        raise ValueError("A release footprint requires one distinct model and one library package")
    supplied = manifests or {}
    packages = []
    for path in package_paths:
        manifest = supplied.get(path.name)
        if manifest is None:
            manifest = verify_package(path)
        elif path.suffix == ".gguf":
            if (
                not isinstance(manifest, dict)
                or manifest.get("kind") != "model"
                or path.stat().st_size != manifest.get("bytes")
                or sha(path) != manifest.get("sha256")
            ):
                raise ValueError("Selected model payload differs from its catalog manifest")
        elif verify_package(path) != manifest:
            raise ValueError("Selected library payload differs from its catalog manifest")
        packages.append(
            dict(
                manifest=manifest,
                package=dict(file=path.name, bytes=path.stat().st_size, sha256=sha(path)),
            )
        )
    if [item["manifest"]["kind"] for item in packages].count("model") != 1 or [
        item["manifest"]["kind"] for item in packages
    ].count("library") != 1:
        raise ValueError("A release footprint requires exactly one model and one library")
    policy = read_installer_policy(installer_source)
    payload_limits = None
    if package_manifest_source is not None:
        payload_limits = read_package_payload_limits(package_manifest_source)
        policy["package_payload_limits"] = payload_limits
        for item in packages:
            kind = item["manifest"]["kind"]
            maximum = payload_limits[f"{kind}_payload_cap_bytes"]
            if item["manifest"]["bytes"] > maximum:
                raise ValueError(f"Selected {kind} package exceeds Android payload limit")
    effective_payload_limits = payload_limits or dict(
        model_payload_cap_bytes=MAX_MODEL_PAYLOAD_BYTES,
        library_payload_cap_bytes=MAX_LIBRARY_PAYLOAD_BYTES,
    )
    storage = storage_values(packages, apk["bytes"], policy["import_headroom_bytes"])
    installed = storage["installed_payload_bytes"]
    strict_cap_passes = installed < policy["installed_payload_cap_bytes"]
    storage_passes = storage["known_steady_bytes"] <= STORAGE_LIMIT_BYTES
    result = dict(
        format=2 if catalog_path is not None else 1,
        complete=True,
        scope=(
            "Verified package bytes and deterministic storage accounting. APK bytes come from the "
            "specified verification report; Android installed APK expansion, conversations, exports, "
            "filesystem overhead and update coexistence are not measured. This is not device RAM or performance evidence."
        ),
        apk_report=dict(file=apk_report_path.as_posix(), sha256=sha(apk_report_path), value=apk),
        installer_policy=dict(file=installer_source.as_posix(), **policy),
        packages=packages,
        import_order=[item["package"]["file"] for item in packages],
        storage=storage,
        checks=dict(
            per_package_payload_caps={
                item["manifest"]["kind"]: dict(
                    limit_bytes=effective_payload_limits[
                        f"{item['manifest']['kind']}_payload_cap_bytes"
                    ],
                    payload_bytes=item["manifest"]["bytes"],
                    margin_bytes=(
                        effective_payload_limits[f"{item['manifest']['kind']}_payload_cap_bytes"]
                        - item["manifest"]["bytes"]
                    ),
                    passes=(
                        item["manifest"]["bytes"]
                        <= effective_payload_limits[f"{item['manifest']['kind']}_payload_cap_bytes"]
                    ),
                )
                for item in packages
            },
            android_installed_payload_cap=dict(
                limit_bytes=policy["installed_payload_cap_bytes"],
                selected_bytes=installed,
                margin_bytes=policy["installed_payload_cap_bytes"] - installed,
                passes=strict_cap_passes,
                comparison="selected installed payload bytes < limit",
            ),
            storage_limit=dict(
                limit_bytes=STORAGE_LIMIT_BYTES,
                known_steady_bytes=storage["known_steady_bytes"],
                margin_before_unmeasured_app_data=STORAGE_LIMIT_BYTES
                - storage["known_steady_bytes"],
                passes=storage_passes,
                comparison="APK archive bytes + selected installed payload bytes <= limit",
            ),
        ),
        interpretation=dict(
            steady="Known lower bound after imported source packages are removed from phone storage.",
            retained_sources="Peak assumes both incoming .blobs files remain on the phone for the full import sequence.",
            move_each_source="Peak assumes each .blobs file is moved off phone storage immediately after successful import.",
            update="Not included. Updating while an old edition remains installed needs old payload + incoming package + new staging payload.",
            importer=(
                "Before each fresh import, the app requires device allocatable bytes to be "
                "strictly greater than the listed threshold."
            ),
        ),
    )
    if catalog_path is not None:
        result["catalog_storage"] = catalog_storage_values(
            catalog_path.read_bytes(),
            apk["bytes"],
            effective_payload_limits,
            policy["installed_payload_cap_bytes"],
        )
    return result


def release_report(apk_report_path: Path, package_paths, installer_source: Path, manifests=None):
    """Create a path-independent footprint manifest suitable for a release kit."""
    catalog_path = next(
        (
            parent / "app/src/main/assets/package-catalog.json"
            for parent in installer_source.resolve().parents
            if (parent / "app/src/main/assets/package-catalog.json").is_file()
        ),
        None,
    )
    if catalog_path is None:
        raise ValueError("Could not locate the catalog paired with the installer source")
    package_manifest_source = installer_source.parent / "PackageManifest.kt"
    if not package_manifest_source.is_file():
        raise ValueError("Could not locate the package payload policy paired with the installer")
    result = calculate(
        apk_report_path,
        package_paths,
        installer_source,
        catalog_path,
        package_manifest_source,
        manifests,
    )
    result["catalog_storage"]["source_file"] = (
        "blobs/android/app/src/main/assets/package-catalog.json"
    )
    result["apk_report"]["file"] = "APK-VERIFICATION.json"
    result["installer_policy"]["file"] = (
        "android/app/src/main/kotlin/dev/blobs/app/data/packages/PackageInstaller.kt"
    )
    result["installer_policy"]["package_payload_limits"]["source_file"] = (
        "android/app/src/main/kotlin/dev/blobs/app/data/packages/PackageManifest.kt"
    )
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk-report", type=Path, required=True)
    parser.add_argument(
        "--package",
        type=Path,
        action="append",
        required=True,
        help="Repeat twice in intended import order: one model and one library",
    )
    parser.add_argument("--installer-source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    package_manifest_source = args.installer_source.parent / "PackageManifest.kt"
    report = calculate(
        args.apk_report,
        args.package,
        args.installer_source,
        package_manifest_source=(
            package_manifest_source if package_manifest_source.is_file() else None
        ),
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    write_json(args.output, report)
    summary = report["storage"]
    print(
        json.dumps(
            {
                key: summary[key]
                for key in [
                    "distribution_bytes",
                    "installed_payload_bytes",
                    "known_steady_bytes",
                    "retained_sources",
                    "move_each_source_after_import",
                ]
            },
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
