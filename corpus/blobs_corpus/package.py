"""Compose source records or GGUF payloads into immutable .blobs archives."""

import argparse
import json
from pathlib import Path
import platform
import tempfile

from .archive import create_package, package, verify_package
from .library import build_library

__all__ = ["create_package", "package", "verify_package"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("kind", choices=["model", "library", "database"])
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--schema", type=int, choices=[1, 2, 3], default=3)
    parser.add_argument(
        "--work-directory",
        type=Path,
        help="Optional database staging volume; final archive is published on the output volume",
    )
    parser.add_argument(
        "--partition-plan",
        type=Path,
        help="Reviewed-source partition plan used only for a library build",
    )
    parser.add_argument(
        "--partition",
        help="Partition ID from --partition-plan, for example p000",
    )
    for name in ["id", "version", "title", "license", "origin"]:
        parser.add_argument("--" + name, required=True)
    args = vars(parser.parse_args())
    source, output, kind = args.pop("input"), args.pop("output"), args.pop("kind")
    schema = args.pop("schema")
    work = args.pop("work_directory")
    partition_plan = args.pop("partition_plan")
    partition_id = args.pop("partition")
    output.parent.mkdir(parents=True, exist_ok=True)

    if kind == "library":
        if (partition_plan is None) != (partition_id is None):
            parser.error("--partition-plan and --partition must be used together")
        work = work or output.parent
        work.mkdir(parents=True, exist_ok=True)
        partition = None
        if partition_plan is not None:
            from .partition import select_partition

            partition = select_partition(source, partition_plan, partition_id)
        with tempfile.TemporaryDirectory(prefix=".build-", dir=work) as folder:
            database = Path(folder) / "library.sqlite"
            build = build_library(source, database, schema=schema, partition=partition)
            manifest = create_package(
                database,
                output,
                kind=kind,
                records_sha256=build["records_sha256"],
                **args,
            )
    else:
        if work is not None or partition_plan is not None or partition_id is not None:
            parser.error("--work-directory and partition options apply to library builds only")
        build = None
        manifest = create_package(
            source,
            output,
            kind="library" if kind == "database" else "model",
            **args,
        )

    from .acquire import digest
    from .extract import write_json

    write_json(
        output.with_suffix(".receipt.json"),
        dict(
            format=1,
            manifest=manifest,
            build=build,
            package=dict(file=output.name, bytes=output.stat().st_size, sha256=digest(output)),
            input_sha256=digest(source),
            python=platform.python_version(),
            platform=platform.system(),
        ),
    )
    print(json.dumps(manifest, indent=2), flush=True)
    print(output, flush=True)
    return manifest


if __name__ == "__main__":
    main()
