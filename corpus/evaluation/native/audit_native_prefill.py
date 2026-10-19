"""Audit a frozen pair of native prefill probes and reject unsafe quality tradeoffs."""

import argparse
import json
from pathlib import Path
import re

from blobs_corpus.extract import write_json
from ..retrieval.host_replay import sha


def load(path: Path):
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"Expected an object: {path}")
    return value


def audit(
    protocol_path: Path,
    manifest_path: Path,
    full_path: Path,
    compact_path: Path,
    staging_path: Path,
    cleanup_path: Path,
    installed_path: Path,
    app_apk_sha256: str,
    probe_test_apk_sha256: str,
):
    protocol, manifest, full, compact, staging, cleanup, installed = map(
        load,
        (
            protocol_path,
            manifest_path,
            full_path,
            compact_path,
            staging_path,
            cleanup_path,
            installed_path,
        ),
    )
    for value in (app_apk_sha256, probe_test_apk_sha256):
        if not re.fullmatch(r"[a-f0-9]{64}", value):
            raise ValueError("APK hashes must be lowercase SHA-256 values")
    configurations = {item["id"]: item for item in manifest.get("configurations", [])}
    if set(configurations) != {"full-five", "compact-three"}:
        raise ValueError("Expected the frozen full and compact configurations")
    full_metrics, compact_metrics = full.get("metrics", {}), compact.get("metrics", {})
    same = (
        full.get("complete") is True
        and compact.get("complete") is True
        and full.get("fingerprint") == compact.get("fingerprint")
        and full.get("model_sha256") == compact.get("model_sha256") == protocol["model_sha256"]
        and full.get("system_sha256")
        == compact.get("system_sha256")
        == manifest["system"]["sha256"]
        and full.get("output_limit") == compact.get("output_limit") == protocol["output_limit"]
        and full.get("user_sha256") == configurations["full-five"]["user"]["sha256"]
        and compact.get("user_sha256") == configurations["compact-three"]["user"]["sha256"]
    )
    for metrics in (full_metrics, compact_metrics):
        if not all(
            type(metrics.get(field)) is int
            for field in (
                "total_us",
                "prefill_us",
                "first_token_callback_us",
                "generation_loop_us",
                "prompt_tokens",
                "output_tokens",
            )
        ):
            raise ValueError("Incomplete native metrics")
        if (
            not metrics["total_us"]
            >= metrics["first_token_callback_us"]
            >= metrics["prefill_us"]
            >= 0
        ):
            raise ValueError("Invalid native stage order")
    full_sources = len(configurations["full-five"]["evidence_indices"])
    compact_sources = len(configurations["compact-three"]["evidence_indices"])
    full_refs = [int(value) for value in re.findall(r"\[(\d+)]", full.get("visible_answer", ""))]
    compact_refs = [
        int(value) for value in re.findall(r"\[(\d+)]", compact.get("visible_answer", ""))
    ]
    full_balanced = full.get("visible_answer", "").count("[") == full.get(
        "visible_answer", ""
    ).count("]")
    compact_refs_valid = bool(compact_refs) and all(
        1 <= value <= compact_sources for value in compact_refs
    )
    checks = dict(
        protocol_and_inputs_match=(
            manifest.get("complete") is True
            and manifest.get("protocol_sha256") == sha(protocol_path)
        ),
        exact_paired_runtime=bool(same),
        exact_model_staging=(
            staging.get("complete") is True
            and staging.get("staged", {}).get("sha256") == protocol["model_sha256"]
        ),
        installed_app_identity=(
            installed.get("complete") is True and installed.get("apk_sha256") == app_apk_sha256
        ),
        compact_prompt_is_smaller=(
            compact_metrics["prompt_tokens"] < full_metrics["prompt_tokens"]
        ),
        compact_prefill_is_lower=(compact_metrics["prefill_us"] < full_metrics["prefill_us"]),
        full_limit_and_truncation_preserved=(
            full.get("result") == 2
            and full_metrics["output_tokens"] == protocol["output_limit"]
            and not full_balanced
        ),
        compact_invalid_citations_preserved=(
            compact.get("result") == 0
            and not compact_refs_valid
            and max(compact_refs, default=0) > compact_sources
        ),
        staged_inputs_removed=(
            cleanup.get("complete") is True and all(cleanup.get("targets_absent", {}).values())
        ),
    )
    if not all(checks.values()):
        failed = ", ".join(key for key, value in checks.items() if not value)
        raise ValueError("Native prefill evidence failed: " + failed)
    token_reduction = 1 - compact_metrics["prompt_tokens"] / full_metrics["prompt_tokens"]
    prefill_reduction = 1 - compact_metrics["prefill_us"] / full_metrics["prefill_us"]
    total_reduction = 1 - compact_metrics["total_us"] / full_metrics["total_us"]
    return dict(
        format=1,
        complete=True,
        decision="reject_both",
        scope=(
            "Two paired native x86_64 emulator probes on one observed development question. "
            "This diagnoses prompt-size costs and quality failures; it is not a phone benchmark "
            "or product evidence-budget change."
        ),
        inputs={
            name: dict(file=path.as_posix(), bytes=path.stat().st_size, sha256=sha(path))
            for name, path in {
                "protocol": protocol_path,
                "input_manifest": manifest_path,
                "full_run": full_path,
                "compact_run": compact_path,
                "staging": staging_path,
                "cleanup": cleanup_path,
                "installed_app": installed_path,
            }.items()
        },
        application=dict(apk_sha256=app_apk_sha256, probe_test_apk_sha256=probe_test_apk_sha256),
        checks=checks,
        comparison=dict(
            full=dict(
                sources=full_sources,
                result=full["result"],
                metrics=full_metrics,
                citations=full_refs,
                balanced_citation_brackets=full_balanced,
            ),
            compact=dict(
                sources=compact_sources,
                result=compact["result"],
                metrics=compact_metrics,
                citations=compact_refs,
                citations_in_range=compact_refs_valid,
            ),
            prompt_token_reduction=token_reduction,
            prefill_time_reduction=prefill_reduction,
            total_native_time_reduction=total_reduction,
        ),
        interpretation={
            "performance": "Less evidence materially lowers prefill, but the compact first callback still exceeds two minutes in this emulator run.",
            "full_quality": "The full prompt reaches the 256-token cap and ends mid-citation.",
            "compact_quality": "The compact answer finishes but cites source numbers that were never supplied.",
            "decision": "Do not ship either 256-token configuration or adopt the three-passage budget from this evidence.",
        },
        script_sha256=sha(Path(__file__)),
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--protocol", type=Path, required=True)
    parser.add_argument("--input-manifest", type=Path, required=True)
    parser.add_argument("--full-run", type=Path, required=True)
    parser.add_argument("--compact-run", type=Path, required=True)
    parser.add_argument("--staging", type=Path, required=True)
    parser.add_argument("--cleanup", type=Path, required=True)
    parser.add_argument("--installed-app", type=Path, required=True)
    parser.add_argument("--app-apk-sha256", required=True)
    parser.add_argument("--probe-test-apk-sha256", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    report = audit(
        args.protocol,
        args.input_manifest,
        args.full_run,
        args.compact_run,
        args.staging,
        args.cleanup,
        args.installed_app,
        args.app_apk_sha256,
        args.probe_test_apk_sha256,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    write_json(args.output, report)
    print(json.dumps({"decision": report["decision"], **report["comparison"]}, indent=2))


if __name__ == "__main__":
    main()
