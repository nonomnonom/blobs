"""Profile Android chat-process PSS while the offline app generates a response."""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import statistics
import subprocess
import time
from datetime import UTC, datetime
from pathlib import Path

from .app_startup import Adb, parse_pss, percentile


def digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def sample_processes(adb: Adb, packages: list[str]) -> dict[str, int]:
    values = {}
    for package in packages:
        try:
            values[package] = parse_pss(adb.text("shell", "dumpsys", "meminfo", package))
        except (subprocess.CalledProcessError, ValueError):
            continue
    return values


def run_case(
    adb: Adb,
    *,
    package: str,
    model_path: str,
    model_sha256: str,
    library_path: str,
    library_sha256: str,
    question: str,
    expected_title: str,
    expected_publisher: str,
    run_id: str,
    sample_interval_seconds: float,
) -> dict:
    encode = lambda text: base64.urlsafe_b64encode(text.encode("utf-8")).decode("ascii").rstrip("=")
    args = [
        "shell", "am", "instrument", "--user", "0", "-w",
        "-e", "class", "dev.blobs.app.NativeInferenceTest#verifiedModelFileProducesSavedCitedAnswer",
        "-e", "modelFile", model_path,
        "-e", "modelSha256", model_sha256,
        "-e", "libraryPackage", library_path,
        "-e", "runId", run_id,
        "-e", "questionBase64", encode(question),
        "-e", "expectedPublisherBase64", encode(expected_publisher),
        "-e", "expectedEvidenceTitleBase64", encode(expected_title),
        "-e", "timeoutSeconds", "240",
        "-e", "requireAirplaneMode", "true",
        f"{package}.test/androidx.test.runner.AndroidJUnitRunner",
    ]
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
    sources = report.get("evidence_sources", [])
    if not any(
        source.get("title") == expected_title
        and source.get("publisher") == expected_publisher
        for source in sources
    ):
        raise ValueError(f"Expected evidence source is absent: {run_id}")

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
        "evidence_sources": report.get("evidence_sources"),
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

    adb = Adb(args.adb, args.serial)
    model = Path(args.model_local)
    library = Path(args.library_local)
    if digest(model) != args.model_sha256:
        raise ValueError("Local model file does not match its declared SHA-256")
    library_sha256 = digest(library)
    fingerprint = adb.text("shell", "getprop", "ro.build.fingerprint")
    sdk = int(adb.text("shell", "getprop", "ro.build.version.sdk"))
    app_info = adb.text("shell", "dumpsys", "package", args.package)
    if f"versionName={args.version_name}" not in app_info:
        raise ValueError("Installed app version differs from the declared version")

    old_airplane = adb.text("shell", "settings", "get", "global", "airplane_mode_on")
    output = []
    try:
        adb.text("shell", "cmd", "connectivity", "airplane-mode", "enable")
        for run_id in args.run_id:
            output.append(
                run_case(
                    adb,
                    package=args.package,
                    model_path=args.model_path,
                    model_sha256=args.model_sha256,
                    library_path=args.library_path,
                    library_sha256=library_sha256,
                    question=args.question,
                    expected_title=args.expected_title,
                    expected_publisher=args.expected_publisher,
                    run_id=run_id,
                    sample_interval_seconds=args.sample_interval,
                )
            )
    finally:
        if old_airplane == "1":
            adb.text("shell", "cmd", "connectivity", "airplane-mode", "enable")
        else:
            adb.text("shell", "cmd", "connectivity", "airplane-mode", "disable")

    combined = [item["memory"]["combined_peak_pss_kib"] for item in output]
    return {
        "format": 1,
        "scope": "Peak sampled Android app and instrumentation PSS during offline chat; no generated answer text is retained or evaluated.",
        "measured_at_utc": datetime.now(UTC).isoformat(),
        "device": {"fingerprint": fingerprint, "sdk": sdk},
        "application": {"package": args.package, "version_name": args.version_name},
        "model_sha256": args.model_sha256,
        "library_sha256": library_sha256,
        "expected_source": {"title": args.expected_title, "publisher": args.expected_publisher},
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
    parser.add_argument("--model-local", required=True, type=Path)
    parser.add_argument("--model-path", required=True)
    parser.add_argument("--model-sha256", required=True)
    parser.add_argument("--library-local", required=True, type=Path)
    parser.add_argument("--library-path", required=True)
    parser.add_argument("--question", required=True)
    parser.add_argument("--expected-title", required=True)
    parser.add_argument("--expected-publisher", required=True)
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
