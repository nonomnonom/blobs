"""Profile a complete corpus build to locate costs before changing languages."""

import argparse
import cProfile
from contextlib import redirect_stdout
from datetime import UTC, datetime
import hashlib
import json
from pathlib import Path
import platform
import pstats
import time

from blobs_corpus.library import build_library


def profile_build(source: Path, output: Path) -> dict:
    source = source.resolve(strict=True)
    with source.open("rb") as stream:
        source_sha256 = hashlib.file_digest(stream, "sha256").hexdigest()
    output.mkdir(parents=True, exist_ok=False)
    database = output / "library.sqlite"
    profiler = cProfile.Profile()
    started = time.perf_counter()
    cpu_started = time.process_time()
    with (output / "build.log").open("w", encoding="utf-8") as log:
        with redirect_stdout(log):
            build = profiler.runcall(build_library, source, database, schema=3)
    wall_seconds = time.perf_counter() - started
    cpu_seconds = time.process_time() - cpu_started
    profiler.dump_stats(str(output / "build.pstats"))
    functions = []
    for (filename, line, name), (primitive, calls, own, cumulative, _) in pstats.Stats(
        profiler
    ).stats.items():
        functions.append(
            {
                "file": Path(filename).name,
                "line": line,
                "function": name,
                "primitive_calls": primitive,
                "calls": calls,
                "self_seconds": own,
                "cumulative_seconds": cumulative,
            }
        )
    report = {
        "format": 1,
        "scope": "Complete schema-3 corpus build under cProfile, including source validation, record decoding, passage splitting, SQLite indexing, names and integrity checks. Profiler overhead is included; this is a bottleneck diagnostic, not Android or uninstrumented throughput.",
        "measured_at_utc": datetime.now(UTC).isoformat(),
        "host": {
            "system": platform.system(),
            "version": platform.version(),
            "machine": platform.machine(),
            "processor": platform.processor(),
            "python": platform.python_version(),
        },
        "profiler_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "input": {"edition": source.parent.name, "file": source.name, "sha256": source_sha256},
        "wall_seconds": wall_seconds,
        "cpu_seconds": cpu_seconds,
        "database_bytes": database.stat().st_size,
        "build": build,
        "top_self_time": sorted(functions, key=lambda row: row["self_seconds"], reverse=True)[:25],
        "top_cumulative_time": sorted(
            functions, key=lambda row: row["cumulative_seconds"], reverse=True
        )[:25],
    }
    with (output / "profile.json").open("x", encoding="utf-8") as stream:
        json.dump(report, stream, indent=2)
        stream.write("\n")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument(
        "output", type=Path, help="New local directory for database, log, pstats, and profile.json"
    )
    args = parser.parse_args()
    report = profile_build(args.source, args.output)
    print(
        json.dumps(
            {
                key: report[key]
                for key in ("wall_seconds", "cpu_seconds", "database_bytes", "top_self_time")
            },
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
