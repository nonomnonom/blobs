"""Publish verified, bounded normalized shards for Git LFS, without raw archives."""

import errno
import os
from pathlib import Path
import shutil
import tempfile

from .extract import verified_shards
from .portfolio import assert_distinct
from .records import documents

MAX_FILE_BYTES = 512 * 1024**2


def export_records(source: Path, output: Path):
    manifest = verified_shards(source)
    if not manifest["complete"] or not manifest.get("curation"):
        raise ValueError("Export a complete, reviewed corpus edition")
    if output.exists():
        raise FileExistsError(output)
    assert_distinct(source)
    files = [item for shard in manifest["shards"] for item in shard["files"].values()]
    if any(item["bytes"] > MAX_FILE_BYTES for item in files):
        raise ValueError("A normalized shard exceeds the 512 MiB artifact budget")
    # Old editions may have valid hashes but predate the current record contract.
    # A reviewed corpus proves its text with the current review policy; a legacy
    # edition carries no such proof, so a body-level replacement character could
    # be silent decode damage rather than an intentional literal.
    legacy_policy = manifest["curation"].get("policy") == "legacy"
    for record in documents(source):
        if legacy_policy and "\ufffd" in record["body"]:
            raise ValueError(
                "Record body contains a Unicode replacement character in a legacy "
                "corpus; re-review the extraction before export"
            )
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".export-", dir=output.parent) as temporary:
        staged = Path(temporary) / "edition"
        staged.mkdir()
        for item in files:
            # Reviewed records are immutable. Copy when crossing filesystem volumes.
            original, target = source.parent / item["name"], staged / item["name"]
            try:
                os.link(original, target)
            except OSError as error:
                if error.errno != errno.EXDEV and getattr(error, "winerror", None) != 17:
                    raise
                shutil.copyfile(original, target)
        shutil.copyfile(source, staged / "manifest.json")
        verified_shards(staged / "manifest.json")
        os.rename(staged, output)
    print(
        f"Exported {len(files)} verified shards; {sum(item['bytes'] for item in files):,} bytes; {output}",
        flush=True,
    )
    return manifest


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    export_records(args.source, args.output)
