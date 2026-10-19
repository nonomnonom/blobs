"""Measure process-cold Android activity launch time and app PSS via adb."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import statistics
import subprocess
from datetime import UTC, datetime
from pathlib import Path


def percentile(values: list[int], fraction: float) -> int:
    """Return a nearest-rank percentile, with a minimum rank of one."""
    ordered = sorted(values)
    rank = max(1, math.ceil(len(ordered) * fraction))
    return ordered[rank - 1]


def parse_launch(output: str) -> dict[str, int | str]:
    fields = dict(re.findall(r"^(Status|LaunchState|TotalTime|WaitTime):\s*(\S+)", output, re.MULTILINE))
    if fields.get("Status") != "ok" or fields.get("LaunchState") != "COLD":
        raise ValueError(f"Expected a successful process-cold launch, got: {fields}")
    if "TotalTime" not in fields or "WaitTime" not in fields:
        raise ValueError(f"Missing activity launch timing fields: {fields}")
    return {
        "launch_state": fields["LaunchState"],
        "total_time_ms": int(fields["TotalTime"]),
        "wait_time_ms": int(fields["WaitTime"]),
    }


def parse_pss(output: str) -> int:
    match = re.search(r"^\s*TOTAL PSS:\s*(\d+)\b", output, re.MULTILINE)
    if not match:
        raise ValueError("Could not find TOTAL PSS in adb dumpsys meminfo output")
    return int(match.group(1))


class Adb:
    def __init__(self, executable: str, serial: str | None):
        self.prefix = [executable]
        if serial:
            self.prefix.extend(["-s", serial])

    def text(self, *args: str) -> str:
        result = subprocess.run(
            [*self.prefix, *args], capture_output=True, text=True, encoding="utf-8",
            check=True, timeout=120
        )
        return result.stdout.strip()

    def bytes(self, *args: str) -> bytes:
        result = subprocess.run(
            [*self.prefix, *args], capture_output=True, check=True, timeout=120
        )
        return result.stdout


def run(args: argparse.Namespace) -> dict:
    adb = Adb(args.adb, args.serial)
    package = args.package
    activity = args.activity or f"{package}/.MainActivity"

    package_dump = adb.text("shell", "dumpsys", "package", package)
    version_match = re.search(r"\bversionName=([^\s]+)", package_dump)
    if not version_match:
        raise RuntimeError(f"Could not determine installed version for {package}")

    apk_paths = [
        line.removeprefix("package:")
        for line in adb.text("shell", "pm", "path", package).splitlines()
        if line.startswith("package:")
    ]
    if not apk_paths:
        raise RuntimeError(f"No installed APK paths found for {package}")
    apk_digests = {
        path.rsplit("/", 1)[-1]: hashlib.sha256(adb.bytes("exec-out", "cat", path)).hexdigest()
        for path in apk_paths
    }

    device = {
        "fingerprint": adb.text("shell", "getprop", "ro.build.fingerprint"),
        "sdk": int(adb.text("shell", "getprop", "ro.build.version.sdk")),
        "device_model": adb.text("shell", "getprop", "ro.product.model"),
    }
    samples = []
    for index in range(1, args.runs + 1):
        adb.text("shell", "am", "force-stop", package)
        launch_output = adb.text("shell", "am", "start", "-W", "-n", activity)
        sample = parse_launch(launch_output)
        sample["run"] = index
        sample["pss_kib"] = parse_pss(adb.text("shell", "dumpsys", "meminfo", package))
        samples.append(sample)

    launch_times = [int(sample["total_time_ms"]) for sample in samples]
    pss_values = [int(sample["pss_kib"]) for sample in samples]
    return {
        "schema_version": 1,
        "measurement": "process-cold-activity-launch",
        "scope": "Repeated force-stop launches on a warmed OS page cache; not first-install, device-cold, or model-inference latency.",
        "measured_at_utc": datetime.now(UTC).isoformat(),
        "package": package,
        "version_name": version_match.group(1),
        "installed_apk_sha256": apk_digests,
        "device": device,
        "runs": samples,
        "summary": {
            "launch_total_time_ms_median": statistics.median(launch_times),
            "launch_total_time_ms_p95_nearest_rank": percentile(launch_times, 0.95),
            "pss_kib_median": statistics.median(pss_values),
            "pss_kib_p95_nearest_rank": percentile(pss_values, 0.95),
        },
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True, help="Path to adb executable")
    parser.add_argument("--serial", help="adb device serial")
    parser.add_argument("--package", default="dev.blobs.app")
    parser.add_argument("--activity", help="Component name; defaults to PACKAGE/.MainActivity")
    parser.add_argument("--runs", type=int, default=7)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if args.runs < 3:
        parser.error("--runs must be at least 3")
    report = run(args)
    with args.output.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(report, stream, indent=2)
        stream.write("\n")
    print(json.dumps(report["summary"], indent=2))


if __name__ == "__main__":
    main()
