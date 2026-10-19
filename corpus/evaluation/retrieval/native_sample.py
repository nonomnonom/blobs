"""Prepare a bounded real-source parity fixture, never a corpus-quality benchmark."""

import argparse
from collections import Counter
import json
import os
from pathlib import Path
import shutil
import tempfile

from libzim.reader import Archive
from blobs_corpus.acquire import digest, verify_source
from blobs_corpus.extract import verified_shards, write_json, write_lines
from blobs_corpus.normalize import article, EXTRACTOR_VERSION
from blobs_corpus.records import documents


def build(source: Path, archive_path: Path, selection: Path, output: Path):
    manifest = verified_shards(source)
    if not manifest["complete"]:
        raise ValueError("Source extraction is incomplete")
    if output.exists():
        raise FileExistsError(output)
    verify_source(archive_path, manifest["source"])
    wanted = {item["title"] for item in documents(selection)}
    if not 1 <= len(wanted) <= 1000:
        raise ValueError("Native parity selection must contain 1–1,000 titles")
    archive = Archive(archive_path)
    output.parent.mkdir(parents=True, exist_ok=True)
    totals, found, shards = Counter(), set(), []
    with tempfile.TemporaryDirectory(prefix=".parity-", dir=output.parent) as temporary:
        staged = Path(temporary) / "sample"
        staged.mkdir()
        for shard in manifest["shards"]:
            selected = []
            for old in documents(source.parent / shard["files"]["documents"]["name"]):
                if old["title"] not in wanted:
                    continue
                path = old["provenance"]["entry_path"]
                html = bytes(archive.get_entry_by_path(path).get_item().content)
                selected.append(article(html, old["title"], path, manifest["source"]))
                found.add(old["title"])
            target = staged / shard["files"]["documents"]["name"]
            write_lines(target, selected)
            files = {
                "documents": dict(
                    name=target.name, bytes=target.stat().st_size, sha256=digest(target)
                )
            }
            if "aliases" in shard["files"]:
                info = shard["files"]["aliases"]
                shutil.copyfile(source.parent / info["name"], staged / info["name"])
                files["aliases"] = info
            totals["documents"] += len(selected)
            shards.append(
                dict(
                    start=shard["start"],
                    end=shard["end"],
                    counts={"documents": len(selected)},
                    files=files,
                )
            )
        result = dict(
            format=1,
            complete=True,
            next_entry=manifest["next_entry"],
            shards=shards,
            source=manifest["source"],
            totals=dict(totals),
            fixture=dict(
                purpose="Native/host query parity only. Not a release corpus or a quality sample.",
                extractor=EXTRACTOR_VERSION,
                input_manifest_sha256=digest(source),
                selection_sha256=digest(selection),
                missing_titles=sorted(wanted - found),
            ),
        )
        write_json(staged / "manifest.json", result)
        verified_shards(staged / "manifest.json")
        os.rename(staged, output)
    print(
        json.dumps(
            dict(documents=totals["documents"], missing_titles=sorted(wanted - found)), indent=2
        ),
        flush=True,
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("archive", type=Path)
    parser.add_argument("selection", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    build(args.source, args.archive, args.selection, args.output)
