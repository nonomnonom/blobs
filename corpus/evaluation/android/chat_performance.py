"""Summarize offline Android chat-path latency without inspecting model text."""

from __future__ import annotations

import argparse
import json
import math
import re
import statistics
from pathlib import Path


_SHA256 = re.compile(r"^[a-f0-9]{64}$")
_APP_STATUSES = {"COMPLETE", "LIMITED", "UNVERIFIED"}


def percentile(values: list[int], fraction: float) -> int:
    ordered = sorted(values)
    return ordered[max(1, math.ceil(len(ordered) * fraction)) - 1]


def summarize(
    run_paths: list[Path],
    installed_app_path: Path,
    expected_title: str,
    expected_publisher: str,
) -> dict:
    if len(run_paths) < 3:
        raise ValueError("At least three successful runs are required")
    installed = json.loads(installed_app_path.read_text(encoding="utf-8"))
    if installed.get("complete") is not True:
        raise ValueError("Installed app identity is incomplete")
    for key in ("app_apk_sha256", "test_apk_sha256"):
        if not _SHA256.fullmatch(installed.get(key, "")):
            raise ValueError(f"Invalid installed APK digest: {key}")

    runs = []
    fingerprints = set()
    model_digests = set()
    library_digests = set()
    app_ids = set()
    seen_ids = set()
    for path in run_paths:
        report = json.loads(path.read_text(encoding="utf-8"))
        if report.get("answer") is not None:
            raise ValueError(f"Refusing a report that contains generated answer text: {path}")
        if not (
            report.get("complete") is True
            and report.get("generation_started") is True
            and report.get("finished_within_timeout") is True
            and report.get("airplane_mode_enabled") is True
        ):
            raise ValueError(f"Run did not complete under the offline test conditions: {path}")
        if report.get("status") not in _APP_STATUSES:
            raise ValueError(f"Unexpected app completion state: {path}")
        if report.get("error") not in (None, ""):
            raise ValueError(f"App reported an error: {path}")

        run_id = report.get("run_id")
        if not isinstance(run_id, str) or not run_id or run_id in seen_ids:
            raise ValueError(f"Missing or duplicate run ID: {path}")
        seen_ids.add(run_id)
        elapsed = report.get("wall_elapsed_ms")
        first_visible = report.get("first_visible_state_ms")
        evidence_ready = report.get("evidence_ready_state_ms")
        if not isinstance(elapsed, int) or elapsed <= 0:
            raise ValueError(f"Missing end-to-end latency: {path}")
        if not isinstance(first_visible, int) or not 0 < first_visible <= elapsed:
            raise ValueError(f"Invalid first-visible latency: {path}")
        if not isinstance(evidence_ready, int) or not 0 < evidence_ready <= first_visible:
            raise ValueError(f"Invalid evidence-ready latency: {path}")

        sources = report.get("evidence_sources")
        if not isinstance(sources, list):
            raise ValueError(f"Missing evidence-source metadata: {path}")
        matching = [
            index + 1
            for index, source in enumerate(sources)
            if source.get("title") == expected_title
            and source.get("publisher") == expected_publisher
        ]
        if not matching:
            raise ValueError(f"Expected source was not retrieved: {path}")

        fingerprints.add(report.get("fingerprint"))
        model_digests.add(report.get("model_sha256"))
        library_digests.add(report.get("library_sha256"))
        app_ids.add(report.get("application_id"))
        runs.append(
            {
                "run_id": run_id,
                "elapsed_ms": elapsed,
                "first_visible_state_ms": first_visible,
                "evidence_ready_state_ms": evidence_ready,
                "visible_after_evidence_ms": first_visible - evidence_ready,
                "app_status": report["status"],
                "evidence_count": report.get("evidence_count"),
                "expected_source_rank": matching[0],
            }
        )

    if len(fingerprints) != 1 or None in fingerprints:
        raise ValueError("Device fingerprint differs across runs")
    if len(model_digests) != 1 or not all(
        _SHA256.fullmatch(x or "") for x in model_digests
    ):
        raise ValueError("Model identity differs across runs")
    if len(library_digests) != 1 or not all(
        _SHA256.fullmatch(x or "") for x in library_digests
    ):
        raise ValueError("Library identity differs across runs")
    if app_ids != {installed.get("package")}:
        raise ValueError("Installed package identity differs from run reports")

    elapsed_values = [run["elapsed_ms"] for run in runs]
    visible_values = [run["first_visible_state_ms"] for run in runs]
    evidence_values = [run["evidence_ready_state_ms"] for run in runs]
    post_evidence_values = [run["visible_after_evidence_ms"] for run in runs]
    return {
        "format": 1,
        "scope": "Offline Android chat-path latency and retrieved-source identity; generated answer text is not stored or evaluated.",
        "device_fingerprint": next(iter(fingerprints)),
        "application": installed,
        "model_sha256": next(iter(model_digests)),
        "library_sha256": next(iter(library_digests)),
        "expected_source": {
            "title": expected_title,
            "publisher": expected_publisher,
        },
        "run_count": len(runs),
        "runs": runs,
        "summary": {
            "elapsed_ms_median": statistics.median(elapsed_values),
            "elapsed_ms_p95_nearest_rank": percentile(elapsed_values, 0.95),
            "elapsed_ms_min": min(elapsed_values),
            "elapsed_ms_max": max(elapsed_values),
            "first_visible_state_ms_median": statistics.median(visible_values),
            "first_visible_state_ms_p95_nearest_rank": percentile(visible_values, 0.95),
            "first_visible_state_ms_min": min(visible_values),
            "first_visible_state_ms_max": max(visible_values),
            "evidence_ready_state_ms_median": statistics.median(evidence_values),
            "evidence_ready_state_ms_p95_nearest_rank": percentile(evidence_values, 0.95),
            "visible_after_evidence_ms_median": statistics.median(post_evidence_values),
            "visible_after_evidence_ms_p95_nearest_rank": percentile(
                post_evidence_values, 0.95
            ),
        },
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", required=True, type=Path, action="append")
    parser.add_argument("--installed-app", required=True, type=Path)
    parser.add_argument("--expected-title", required=True)
    parser.add_argument("--expected-publisher", required=True)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    result = summarize(
        args.run,
        args.installed_app,
        args.expected_title,
        args.expected_publisher,
    )
    with args.output.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(result, stream, indent=2)
        stream.write("\n")
    print(json.dumps(result["summary"], indent=2))


if __name__ == "__main__":
    main()
