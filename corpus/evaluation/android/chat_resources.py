"""Profile Android chat-process PSS while the offline app generates a response."""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import re
import statistics
import subprocess
import time
from datetime import UTC, datetime
from pathlib import Path

from blobs_corpus.package import verify_package
from .app_startup import Adb, parse_pss, percentile


def digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def library_payload_sha256(path: Path) -> str:
    manifest = verify_package(path)
    if manifest["kind"] != "library":
        raise ValueError("Local package is not a library")
    return manifest["sha256"]


def parse_total_ram_kib(output: str) -> int:
    match = re.search(r"^MemTotal:\s*(\d+)\s+kB\s*$", output, re.MULTILINE)
    if not match:
        raise ValueError("Could not find MemTotal in /proc/meminfo")
    return int(match.group(1))


def parse_data_filesystem(output: str) -> dict[str, int | str]:
    rows = [line.split() for line in output.splitlines() if line.strip()]
    if len(rows) < 2 or len(rows[-1]) < 6:
        raise ValueError("Could not parse df output for /data")
    row = rows[-1]
    try:
        size_kib, used_kib, available_kib = map(int, row[1:4])
    except ValueError as error:
        raise ValueError("Invalid df byte counts for /data") from error
    return {
        "size_kib": size_kib,
        "used_kib": used_kib,
        "available_kib": available_kib,
        "mount": row[-1],
    }


def decode_base64_text(value: str) -> str:
    padded = value + "=" * (-len(value) % 4)
    try:
        return base64.b64decode(padded, altchars=b"-_", validate=True).decode("utf-8")
    except (ValueError, UnicodeDecodeError) as error:
        raise ValueError("Invalid URL-safe Base64 UTF-8 value") from error


def sample_processes(adb: Adb, packages: list[str]) -> dict[str, int]:
    values = {}
    for package in packages:
        try:
            values[package] = parse_pss(adb.text("shell", "dumpsys", "meminfo", package))
        except (subprocess.CalledProcessError, ValueError):
            continue
    return values


def package_source_arguments(
    *,
    model_path: str | None,
    installed_model_key: str | None,
    model_sha256: str,
    library_path: str | None,
    installed_library_key: str | None,
) -> list[str]:
    args = []
    if installed_model_key is not None:
        args.extend(["-e", "installedModel", installed_model_key])
    else:
        args.extend(["-e", "modelFile", str(model_path), "-e", "modelSha256", model_sha256])
    if installed_library_key is not None:
        args.extend(["-e", "installedLibrary", installed_library_key])
    else:
        args.extend(["-e", "libraryPackage", str(library_path)])
    return args


def run_case(
    adb: Adb,
    *,
    package: str,
    model_path: str | None,
    installed_model_key: str | None,
    model_sha256: str,
    library_path: str | None,
    installed_library_key: str | None,
    library_sha256: str,
    question: str,
    run_id: str,
    sample_interval_seconds: float,
) -> dict:
    encode = lambda text: base64.urlsafe_b64encode(text.encode("utf-8")).decode("ascii").rstrip("=")
    test_method = (
        "installedLibraryAndModelCompleteOfflineChatFlow"
        if installed_model_key is not None
        else "verifiedModelFileCompletesOfflineChatFlow"
    )
    args = [
        "shell", "am", "instrument", "--user", "0", "-w",
        "-e", "class", f"dev.blobs.app.NativeInferenceTest#{test_method}",
        "-e", "runId", run_id,
        "-e", "questionBase64", encode(question),
        "-e", "timeoutSeconds", "240",
        "-e", "requireAirplaneMode", "true",
    ]
    args.extend(
        package_source_arguments(
            model_path=model_path,
            installed_model_key=installed_model_key,
            model_sha256=model_sha256,
            library_path=library_path,
            installed_library_key=installed_library_key,
        )
    )
    args.append(f"{package}.test/androidx.test.runner.AndroidJUnitRunner")
    start = time.monotonic()
    process = subprocess.Popen(
        [*adb.prefix, *args], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True
    )
    samples = []
    while process.poll() is None:
        sample = sample_processes(adb, [package, f"{package}.test"])
        if sample:
            samples.append({"elapsed_ms": round((time.monotonic() - start) * 1000), **sample})
        time.sleep(sample_interval_seconds)
    output, _ = process.communicate(timeout=30)
    elapsed_ms = round((time.monotonic() - start) * 1000)
    if process.returncode != 0 or "OK (1 test)" not in output:
        raise RuntimeError(f"Offline chat test failed ({run_id}):\n{output[-4000:]}")
    if not samples:
        raise RuntimeError(f"No process memory samples were collected ({run_id})")

    report_text = adb.text("exec-out", "run-as", package, "cat", f"files/answer-{run_id}.json")
    report = json.loads(report_text)
    if report.get("answer") is not None or not report.get("airplane_mode_enabled"):
        raise ValueError(f"Instrumentation report is not a text-free offline run: {run_id}")
    if report.get("complete") is not True or report.get("finished_within_timeout") is not True:
        raise ValueError(f"Instrumentation report is incomplete: {run_id}")
    if report.get("model_sha256") != model_sha256:
        raise ValueError(f"Instrumentation used an unexpected model: {run_id}")
    if report.get("library_sha256") != library_sha256:
        raise ValueError(f"Instrumentation used an unexpected library: {run_id}")
    if report.get("status") not in {"COMPLETE", "LIMITED", "UNVERIFIED"}:
        raise ValueError(f"Instrumentation returned an unexpected app state: {run_id}")
    per_process = {}
    for name in (package, f"{package}.test"):
        numbers = [sample[name] for sample in samples if name in sample]
        if numbers:
            per_process[name] = {"peak_pss_kib": max(numbers), "sample_count": len(numbers)}
    combined_values = [
        sum(value for name, value in sample.items() if name != "elapsed_ms")
        for sample in samples
    ]
    return {
        "run_id": run_id,
        "elapsed_ms": elapsed_ms,
        "airplane_mode_enabled": True,
        "app_status": report.get("status"),
        "evidence_count": report.get("evidence_count"),
        "model_sha256": report.get("model_sha256"),
        "library_sha256": report.get("library_sha256"),
        "memory": {
            "process_peak_pss_kib": per_process,
            "combined_peak_pss_kib": max(combined_values),
            "sample_count": len(samples),
            "sample_interval_seconds": sample_interval_seconds,
        },
    }


def run(args: argparse.Namespace) -> dict:
    if len(args.run_id) < 3 or len(set(args.run_id)) != len(args.run_id):
        raise ValueError("Provide at least three unique run IDs")
    if args.sample_interval < 0.1 or args.sample_interval > 5:
        raise ValueError("Sample interval must be between 0.1 and 5 seconds")
    if len(args.model_sha256) != 64 or any(char not in "0123456789abcdef" for char in args.model_sha256):
        raise ValueError("Invalid model SHA-256")
    for key in (args.installed_model_key, args.installed_library_key):
        if key is not None and not re.fullmatch(r"[a-zA-Z0-9.-]{1,200}", key):
            raise ValueError("Invalid installed package key")

    adb = Adb(args.adb, args.serial)
    if args.model_local is not None:
        if not args.model_path:
            raise ValueError("--model-path is required with --model-local")
        if digest(args.model_local) != args.model_sha256:
            raise ValueError("Local model file does not match its declared SHA-256")
    elif args.model_path:
        raise ValueError("--model-path only applies with --model-local")

    if args.library_local is not None:
        if not args.library_path:
            raise ValueError("--library-path is required with --library-local")
        library_sha256 = library_payload_sha256(args.library_local)
        if args.library_sha256 is not None and args.library_sha256 != library_sha256:
            raise ValueError("Library payload digest differs from --library-sha256")
    else:
        if args.library_path:
            raise ValueError("--library-path only applies with --library-local")
        library_sha256 = args.library_sha256
        if library_sha256 is None or not re.fullmatch(r"[a-f0-9]{64}", library_sha256):
            raise ValueError("--library-sha256 is required for an installed library")
    fingerprint = adb.text("shell", "getprop", "ro.build.fingerprint")
    sdk = int(adb.text("shell", "getprop", "ro.build.version.sdk"))
    device_model = adb.text("shell", "getprop", "ro.product.model")
    supported_abis = adb.text("shell", "getprop", "ro.product.cpu.abilist")
    total_ram_kib = parse_total_ram_kib(adb.text("shell", "cat", "/proc/meminfo"))
    data_filesystem = parse_data_filesystem(adb.text("shell", "df", "-k", "/data"))
    app_info = adb.text("shell", "dumpsys", "package", args.package)
    if f"versionName={args.version_name}" not in app_info:
        raise ValueError("Installed app version differs from the declared version")

    old_airplane = adb.text("shell", "settings", "get", "global", "airplane_mode_on")
    old_wifi = adb.text("shell", "settings", "get", "global", "wifi_on")
    output = []
    try:
        adb.text("shell", "cmd", "connectivity", "airplane-mode", "enable")
        adb.text("shell", "svc", "wifi", "disable")
        for run_id in args.run_id:
            output.append(
                run_case(
                    adb,
                    package=args.package,
                    model_path=args.model_path,
                    installed_model_key=args.installed_model_key,
                    model_sha256=args.model_sha256,
                    library_path=args.library_path,
                    installed_library_key=args.installed_library_key,
                    library_sha256=library_sha256,
                    question=args.question,
                    run_id=run_id,
                    sample_interval_seconds=args.sample_interval,
                )
            )
    finally:
        if old_airplane == "1":
            adb.text("shell", "cmd", "connectivity", "airplane-mode", "enable")
        else:
            adb.text("shell", "cmd", "connectivity", "airplane-mode", "disable")
        if old_wifi == "1":
            adb.text("shell", "svc", "wifi", "enable")
        else:
            adb.text("shell", "svc", "wifi", "disable")

    combined = [item["memory"]["combined_peak_pss_kib"] for item in output]
    return {
        "format": 1,
        "scope": "Peak sampled Android app and instrumentation PSS during offline chat; no generated answer text is retained or evaluated.",
        "measured_at_utc": datetime.now(UTC).isoformat(),
        "device": {
            "fingerprint": fingerprint,
            "sdk": sdk,
            "model": device_model,
            "supported_abis": supported_abis.split(",") if supported_abis else [],
            "total_ram_kib": total_ram_kib,
            "data_filesystem": data_filesystem,
        },
        "application": {"package": args.package, "version_name": args.version_name},
        "model_sha256": args.model_sha256,
        "library_sha256": library_sha256,
        "runs": output,
        "summary": {
            "run_count": len(output),
            "combined_peak_pss_kib_median": statistics.median(combined),
            "combined_peak_pss_kib_p95_nearest_rank": percentile(combined, 0.95),
            "combined_peak_pss_kib_max": max(combined),
        },
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial")
    parser.add_argument("--package", default="dev.blobs.app.debug")
    parser.add_argument("--version-name", required=True)
    model_source = parser.add_mutually_exclusive_group(required=True)
    model_source.add_argument("--model-local", type=Path)
    model_source.add_argument("--installed-model-key")
    parser.add_argument("--model-path")
    parser.add_argument("--model-sha256", required=True)
    library_source = parser.add_mutually_exclusive_group(required=True)
    library_source.add_argument("--library-local", type=Path)
    library_source.add_argument("--installed-library-key")
    parser.add_argument("--library-path")
    parser.add_argument("--library-sha256")
    parser.add_argument("--question", required=True)
    parser.add_argument("--run-id", required=True, action="append")
    parser.add_argument("--sample-interval", type=float, default=0.5)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    report = run(args)
    with args.output.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(report, stream, indent=2)
        stream.write("\n")
    print(json.dumps(report["summary"], indent=2))


if __name__ == "__main__":
    main()
