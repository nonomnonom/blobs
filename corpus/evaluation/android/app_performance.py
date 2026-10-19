"""Capture startup, search, response-start, cancellation and memory for one Android profile."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import statistics
import subprocess


def capture(adb: str, serial: str, profile: str, model_key: str, output: Path) -> dict:
    if not re.fullmatch(r"[a-zA-Z0-9-]{1,80}", profile):
        raise ValueError("Use a short profile name containing letters, digits and hyphens")
    if not re.fullmatch(r"[a-zA-Z0-9][a-zA-Z0-9.-]{0,160}", model_key):
        raise ValueError("Supply the key of an installed model")
    output.mkdir(parents=True, exist_ok=True)
    package = "dev.blobs.app.debug"

    def run(*arguments: str, timeout: int = 120) -> str:
        return subprocess.check_output(
            [adb, "-s", serial, *arguments],
            text=True,
            encoding="utf-8",
            timeout=timeout,
        )

    identities = {}
    for app_id in [package, f"{package}.test"]:
        path = run("shell", "pm", "path", app_id).strip().removeprefix("package:")
        if not re.fullmatch(r"/data/app/[a-zA-Z0-9._~+/=-]+/base\.apk", path):
            raise ValueError(f"Expected one installed base APK for {app_id}")
        digest = run("shell", "sha256sum", path).split()[0]
        if not re.fullmatch(r"[a-f0-9]{64}", digest):
            raise ValueError("Could not identify the installed APK")
        identities[app_id] = digest
    startup = []
    for _ in range(5):
        run("shell", "am", "force-stop", package)
        result = run("shell", "am", "start", "-W", "-n", f"{package}/dev.blobs.app.MainActivity")
        match = re.search(r"^TotalTime:\s*(\d+)\s*$", result, re.MULTILINE)
        if "Status: ok" not in result or match is None:
            raise ValueError(f"Android did not report a successful cold start: {result}")
        startup.append(int(match[1]))
    log = run(
        "shell",
        "am",
        "instrument",
        "-w",
        "-r",
        "-e",
        "notAnnotation",
        "none",
        "-e",
        "class",
        "dev.blobs.app.AppJourneyPerformanceTest",
        "-e",
        "performanceProfile",
        profile,
        "-e",
        "performanceModel",
        model_key,
        f"{package}.test/androidx.test.runner.AndroidJUnitRunner",
        timeout=900,
    )
    (output / f"{profile}.log").write_text(log, encoding="utf-8")
    if "OK (1 test)" not in log or "AssumptionViolated" in log:
        raise ValueError("Performance capture failed; inspect the instrumentation log")
    report = json.loads(
        run(
            "shell",
            "cat",
            f"/sdcard/Android/data/{package}/files/performance/{profile}.json",
        )
    )
    if (
        report.get("complete") is not True
        or report.get("profile") != profile
        or len(report.get("responses", [])) != 3
        or len(report.get("searches", [])) != 15
    ):
        raise ValueError("Incomplete or mismatched performance report")
    report["apk_sha256"] = identities
    report["startup"] = {
        "metric": "Android am start -W TotalTime, process-cold, OS caches retained",
        "runs_ms": startup,
        "median_ms": statistics.median(startup),
    }
    report["memory_metric"] = (
        "Peak observed PSS, sampled once per second; not a hardware prediction"
    )
    (output / f"{profile}.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--profile", required=True)
    parser.add_argument("--model-key", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = capture(args.adb, args.serial, args.profile, args.model_key, args.output)
    print(f"{args.profile}: complete; median startup {report['startup']['median_ms']} ms")


if __name__ == "__main__":
    main()
