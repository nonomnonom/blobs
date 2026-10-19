"""Compare repeated, audited native model smoke runs without claiming answer quality."""

import argparse
import json
from pathlib import Path
import re
import statistics

from blobs_corpus.extract import write_json
from ..retrieval.host_replay import sha


def load(path: Path):
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict) or value.get("complete") is not True:
        raise ValueError(f"Expected a complete native smoke audit: {path}")
    if not value.get("checks") or not all(value["checks"].values()):
        raise ValueError(f"Native smoke audit contains failed checks: {path}")
    return value


def summarize(paths):
    if len(paths) < 3:
        raise ValueError("Require at least three audited runs per build")
    reports = [load(path) for path in paths]
    warm_presence = [
        report["observations"].get("warm_inference_metrics") is not None for report in reports
    ]
    if any(warm_presence) and not all(warm_presence):
        raise ValueError("All runs in one model group must include warm-turn metrics")
    fingerprints = {
        report["observations"].get("device_fingerprint") or report.get("device_fingerprint")
        for report in reports
    }
    device_profiles = {
        json.dumps(
            {
                key: value
                for key, value in report.get("device_profile", {}).items()
                if key != "serial"
            },
            sort_keys=True,
        )
        for report in reports
    }
    models = {(report["model"]["bytes"], report["model"]["sha256"]) for report in reports}
    applications = {
        (report["application"]["apk_sha256"], report["application"]["test_apk_sha256"])
        for report in reports
    }
    runtimes = {report.get("runtime_commit") for report in reports}
    workloads = {json.dumps(report.get("workload"), sort_keys=True) for report in reports}
    thread_modes = {report.get("thread_mode", "adaptive") for report in reports}
    requested_threads = {report.get("requested_inference_threads") for report in reports}
    if len(fingerprints) != 1 or None in fingerprints:
        raise ValueError("Runs must use the same identified device")
    if len(device_profiles) != 1 or "null" in device_profiles:
        raise ValueError("Runs must use the same device profile")
    if len(models) != 1 or len(applications) != 1:
        raise ValueError("Runs must use the same model and installed build")
    if len(runtimes) != 1 or None in runtimes:
        raise ValueError("Runs must use the same pinned runtime")
    if len(workloads) != 1 or "null" in workloads:
        raise ValueError("Runs must use the same fixed workload")
    if len(thread_modes) != 1 or len(requested_threads) != 1:
        raise ValueError("Runs in one build must use the same thread-selection mode")

    rows = []
    warm_rows = []
    for path, report in zip(paths, reports):
        observation = report["observations"]
        metrics = observation.get("inference_metrics")
        if not isinstance(metrics, dict):
            raise ValueError("Native smoke audit lacks stage metrics")
        required = (
            "total_us",
            "first_token_callback_us",
            "context_setup_us",
            "prefill_us",
            "model_load_us",
            "generation_loop_us",
            "prompt_tokens",
            "output_tokens",
            "inference_threads",
        )
        if any(type(metrics.get(field)) is not int or metrics[field] < 0 for field in required):
            raise ValueError("Native smoke metrics are incomplete or invalid")
        if (
            not metrics["prompt_tokens"]
            or not metrics["output_tokens"]
            or not metrics["generation_loop_us"]
            or not 1 <= metrics["inference_threads"] <= 4
        ):
            raise ValueError("Native smoke did not produce complete inference measurements")
        if not metrics["total_us"] >= metrics["first_token_callback_us"] >= metrics["prefill_us"]:
            raise ValueError("Native smoke timing stages are out of order")
        rows.append(
            dict(
                audit_file=path.name,
                audit_sha256=sha(path),
                run_file=report["inputs"]["run"]["file"],
                run_sha256=report["inputs"]["run"]["sha256"],
                prompt_tokens=metrics["prompt_tokens"],
                output_tokens=metrics["output_tokens"],
                total_ms=metrics["total_us"] / 1000,
                first_token_ms=metrics["first_token_callback_us"] / 1000,
                context_setup_ms=metrics["context_setup_us"] / 1000,
                prefill_ms=metrics["prefill_us"] / 1000,
                model_load_ms=metrics["model_load_us"] / 1000,
                inference_threads=metrics["inference_threads"],
                thread_mode=report.get("thread_mode", "adaptive"),
                decode_tokens_per_second=(
                    metrics["output_tokens"] * 1_000_000 / metrics["generation_loop_us"]
                    if metrics["generation_loop_us"]
                    else None
                ),
            )
        )

        warm = report["observations"].get("warm_inference_metrics")
        if warm is not None:
            if (
                not isinstance(warm, dict)
                or warm.get("model_cache_hit") is not True
                or warm.get("model_load_us") != 0
                or warm.get("prompt_tokens") != metrics["prompt_tokens"]
                or warm.get("output_tokens") != metrics["output_tokens"]
                or warm.get("inference_threads") != metrics["inference_threads"]
            ):
                raise ValueError(
                    "Warm-turn cache evidence must have the same output token count as the cold workload"
                )
            warm_required = (
                "total_us",
                "first_token_callback_us",
                "context_setup_us",
                "prefill_us",
                "model_load_us",
                "generation_loop_us",
                "prompt_tokens",
                "output_tokens",
                "inference_threads",
            )
            if any(type(warm.get(field)) is not int or warm[field] < 0 for field in warm_required):
                raise ValueError("Warm-turn native metrics are incomplete or invalid")
            if not warm["total_us"] >= warm["first_token_callback_us"] >= warm["prefill_us"]:
                raise ValueError("Warm-turn timing stages are out of order")
            warm_rows.append(
                dict(
                    audit_file=path.name,
                    audit_sha256=sha(path),
                    run_file=report["inputs"]["run"]["file"],
                    run_sha256=report["inputs"]["run"]["sha256"],
                    prompt_tokens=warm["prompt_tokens"],
                    output_tokens=warm["output_tokens"],
                    total_ms=warm["total_us"] / 1000,
                    first_token_ms=warm["first_token_callback_us"] / 1000,
                    context_setup_ms=warm["context_setup_us"] / 1000,
                    prefill_ms=warm["prefill_us"] / 1000,
                    model_load_ms=warm["model_load_us"] / 1000,
                    inference_threads=warm["inference_threads"],
                    thread_mode=report.get("thread_mode", "adaptive"),
                    decode_tokens_per_second=(
                        warm["output_tokens"] * 1_000_000 / warm["generation_loop_us"]
                        if warm["generation_loop_us"]
                        else None
                    ),
                )
            )

    fields = (
        "total_ms",
        "first_token_ms",
        "context_setup_ms",
        "prefill_ms",
        "model_load_ms",
        "decode_tokens_per_second",
        "output_tokens",
        "inference_threads",
    )
    medians = {field: statistics.median(row[field] for row in rows) for field in fields}
    ranges = {
        field: [min(row[field] for row in rows), max(row[field] for row in rows)]
        for field in fields
    }
    prompt_counts = {row["prompt_tokens"] for row in rows}
    if len(prompt_counts) != 1:
        raise ValueError("Runs in one model group must have identical prompt token counts")
    output_counts = {row["output_tokens"] for row in rows}
    if len(output_counts) != 1:
        raise ValueError("Runs in one model group must have identical output token counts")
    if warm_rows and len(warm_rows) != len(rows):
        raise ValueError("All runs in one model group must include warm-turn metrics")
    warm_medians = None
    warm_ranges = None
    if warm_rows:
        warm_medians = {
            field: statistics.median(row[field] for row in warm_rows) for field in fields
        }
        warm_ranges = {
            field: [min(row[field] for row in warm_rows), max(row[field] for row in warm_rows)]
            for field in fields
        }
    return dict(
        model=dict(bytes=next(iter(models))[0], sha256=next(iter(models))[1]),
        device_fingerprint=next(iter(fingerprints)),
        device_profile=json.loads(next(iter(device_profiles))),
        apk_sha256=next(iter(applications))[0],
        test_apk_sha256=next(iter(applications))[1],
        runtime_commit=next(iter(runtimes)),
        workload=json.loads(next(iter(workloads))),
        thread_mode=next(iter(thread_modes)),
        requested_inference_threads=next(iter(requested_threads)),
        prompt_tokens=next(iter(prompt_counts)),
        output_tokens=next(iter(output_counts)),
        runs=rows,
        median=medians,
        range=ranges,
        warm_turn=dict(runs=warm_rows, median=warm_medians, range=warm_ranges)
        if warm_rows
        else None,
    )


def compare(baseline_paths, candidate_paths, runtime_lock_path: Path):
    baseline, candidate = summarize(baseline_paths), summarize(candidate_paths)
    runtime_lock = load_runtime_lock(runtime_lock_path)
    if baseline["device_fingerprint"] != candidate["device_fingerprint"]:
        raise ValueError("Baseline and candidate must use the same device fingerprint")
    if baseline["device_profile"] != candidate["device_profile"]:
        raise ValueError("Baseline and candidate must use the same device profile")
    if baseline["model"] != candidate["model"]:
        raise ValueError("Baseline and candidate must use the same exact model")
    if baseline["runtime_commit"] != candidate["runtime_commit"]:
        raise ValueError("Baseline and candidate must use the same pinned runtime")
    if baseline["runtime_commit"] != runtime_lock.get("commit"):
        raise ValueError("Native runs do not match the supplied runtime lock")
    if baseline["workload"] != candidate["workload"]:
        raise ValueError("Baseline and candidate must use the same fixed workload")
    if baseline["prompt_tokens"] != candidate["prompt_tokens"]:
        raise ValueError("Baseline and candidate must use identical prompt token counts")
    if baseline["output_tokens"] != candidate["output_tokens"]:
        raise ValueError("Baseline and candidate must produce the same output token count")
    before, after = baseline["median"], candidate["median"]
    warm_comparison = None
    if candidate["warm_turn"] is not None:
        warm_after = candidate["warm_turn"]["median"]
        if warm_after["output_tokens"] != baseline["output_tokens"]:
            raise ValueError("Warm-turn runs must produce the same output token count as baseline")
        warm_comparison = dict(
            reference="Baseline build per-turn model load vs candidate build after the model is cached",
            baseline_cold_median=dict(
                total_ms=before["total_ms"],
                first_token_ms=before["first_token_ms"],
                decode_tokens_per_second=before["decode_tokens_per_second"],
            ),
            candidate_warm_median=dict(
                total_ms=warm_after["total_ms"],
                first_token_ms=warm_after["first_token_ms"],
                model_load_ms=warm_after["model_load_ms"],
                decode_tokens_per_second=warm_after["decode_tokens_per_second"],
            ),
            candidate_warm_vs_baseline_cold_percent=dict(
                total_latency=100 * (warm_after["total_ms"] / before["total_ms"] - 1),
                first_token_latency=100
                * (warm_after["first_token_ms"] / before["first_token_ms"] - 1),
                decode_tokens_per_second=100
                * (warm_after["decode_tokens_per_second"] / before["decode_tokens_per_second"] - 1),
            ),
            candidate_warm_vs_candidate_cold_percent=dict(
                total_latency=100 * (warm_after["total_ms"] / after["total_ms"] - 1),
                first_token_latency=100
                * (warm_after["first_token_ms"] / after["first_token_ms"] - 1),
                decode_tokens_per_second=100
                * (warm_after["decode_tokens_per_second"] / after["decode_tokens_per_second"] - 1),
            ),
        )
    return dict(
        format=1,
        complete=True,
        scope=(
            "Repeated native smoke comparison on the same Android device, with the same model, "
            "fixed workload and pinned llama.cpp runtime. Baseline and candidate app builds may "
            "differ. This checks "
            "basic model compatibility and latency only; it is not RAG quality, phone performance, "
            "or a production-model selection."
        ),
        runtime=dict(commit=runtime_lock["commit"], lock_sha256=sha(runtime_lock_path)),
        device_fingerprint=baseline["device_fingerprint"],
        device_profile=baseline["device_profile"],
        application=dict(
            baseline=dict(
                apk_sha256=baseline["apk_sha256"], test_apk_sha256=baseline["test_apk_sha256"]
            ),
            candidate=dict(
                apk_sha256=candidate["apk_sha256"], test_apk_sha256=candidate["test_apk_sha256"]
            ),
        ),
        baseline=baseline,
        candidate=candidate,
        candidate_vs_baseline_percent=dict(
            total_latency=100 * (after["total_ms"] / before["total_ms"] - 1),
            first_token_latency=100 * (after["first_token_ms"] / before["first_token_ms"] - 1),
            context_setup_latency=100
            * (after["context_setup_ms"] / before["context_setup_ms"] - 1),
            decode_tokens_per_second=100
            * (after["decode_tokens_per_second"] / before["decode_tokens_per_second"] - 1),
        ),
        candidate_warm_turn=candidate["warm_turn"],
        candidate_warm_turn_vs_baseline_cold=warm_comparison,
        interpretation=(
            "A lower total/first-token latency or higher decode rate is favorable in this "
            "probe only. It measures app/runtime behavior, not generated-content quality."
        ),
    )


def load_runtime_lock(path: Path):
    value = json.loads(path.read_text(encoding="utf-8"))
    if (
        not isinstance(value, dict)
        or not isinstance(value.get("commit"), str)
        or not re.fullmatch(r"[a-f0-9]{40}", value["commit"])
    ):
        raise ValueError("Runtime lock lacks a pinned commit")
    return value


def record(paths, runtime_lock_path: Path):
    result = summarize(paths)
    runtime_lock = load_runtime_lock(runtime_lock_path)
    if result["runtime_commit"] != runtime_lock["commit"]:
        raise ValueError("Native runs do not match the supplied runtime lock")
    return dict(
        format=1,
        complete=True,
        scope=(
            "Repeated native inference timing on one identified Android device using one exact "
            "model, fixed workload and pinned llama.cpp runtime. This records application "
            "latency only; no generated text is retained or graded."
        ),
        runtime=dict(commit=runtime_lock["commit"], lock_sha256=sha(runtime_lock_path)),
        model=result["model"],
        device_fingerprint=result["device_fingerprint"],
        device_profile=result["device_profile"],
        application=dict(
            apk_sha256=result["apk_sha256"], test_apk_sha256=result["test_apk_sha256"]
        ),
        workload=result["workload"],
        prompt_tokens=result["prompt_tokens"],
        output_tokens=result["output_tokens"],
        runs=result["runs"],
        median=result["median"],
        range=result["range"],
        warm_turn=result["warm_turn"],
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline-audit", type=Path, nargs="+", required=True)
    parser.add_argument("--candidate-audit", type=Path, nargs="+")
    parser.add_argument("--runtime-lock", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    if args.candidate_audit:
        report = compare(args.baseline_audit, args.candidate_audit, args.runtime_lock)
        report["script_sha256"] = sha(Path(__file__))
        report["inputs"] = dict(
            baseline_audits=[
                dict(file=path.as_posix(), sha256=sha(path)) for path in args.baseline_audit
            ],
            candidate_audits=[
                dict(file=path.as_posix(), sha256=sha(path)) for path in args.candidate_audit
            ],
            runtime_lock=dict(file=args.runtime_lock.as_posix(), sha256=sha(args.runtime_lock)),
        )
        output = dict(
            baseline_median=report["baseline"]["median"],
            candidate_median=report["candidate"]["median"],
            candidate_vs_baseline_percent=report["candidate_vs_baseline_percent"],
        )
    else:
        report = record(args.baseline_audit, args.runtime_lock)
        report["inputs"] = dict(
            audits=[dict(file=path.as_posix(), sha256=sha(path)) for path in args.baseline_audit],
            runtime_lock=dict(file=args.runtime_lock.as_posix(), sha256=sha(args.runtime_lock)),
        )
        report["script_sha256"] = sha(Path(__file__))
        output = dict(median=report["median"], range=report["range"])
    args.output.parent.mkdir(parents=True, exist_ok=True)
    write_json(args.output, report)
    print(json.dumps(output, indent=2))


if __name__ == "__main__":
    main()
