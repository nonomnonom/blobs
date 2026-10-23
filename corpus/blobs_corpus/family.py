"""Compose one reviewed edition from several verified per-archive extractions.

A subject lane such as practical how-to or repair spans many site archives. Shipping
one library per site would rebuild the fragment packs the registry retires, so the
lane merges complete per-archive editions into one record stream that curate, export,
and package can treat as an ordinary edition.
"""

from collections import Counter
import errno
import json
import os
import shutil
from pathlib import Path

from .acquire import digest
from .extract import verified_shards, write_json
from .portfolio import ENGLISH_TAGS

FORMAT = 1


def _language(manifest: dict) -> str:
    return str((manifest.get("archive_metadata") or {}).get("Language", "")).lower()


def _build_source(manifests: list[dict], lane: str, edition: str, collection: str, scope: str) -> dict:
    """Describe the lane as one source whose licence and publisher stay per record."""
    licenses = sorted({manifest["source"]["license"] for manifest in manifests})
    publishers = sorted({manifest["source"]["publisher"] for manifest in manifests})
    origins = sorted({manifest["source"]["url"] for manifest in manifests})
    return {
        "id": lane,
        "edition": edition,
        "collection": collection,
        "publisher": "; ".join(publishers),
        "license": "; ".join(licenses),
        "scope": scope,
        "origins": origins,
    }


def merge(
    member_paths: list[Path], output: Path, lane: str, edition: str, collection: str, scope: str
):
    members = []
    seen_urls: dict[str, str] = {}
    seen_ids: dict[str, str] = {}
    base_identity = None
    base_language = None
    for path in member_paths:
        manifest = verified_shards(path)
        if not manifest["complete"]:
            raise ValueError(f"Finish extraction before merging: {path.parent.name}")
        identity = manifest["identity"]
        language = _language(manifest)
        if language not in ENGLISH_TAGS:
            raise ValueError(
                f"Supply language policy: Blobs publishes English knowledge only, and archive edition "
                f"{path.parent.name} is {language!r}"
            )
        if base_identity is None:
            base_identity = {k: identity[k] for k in ("extractor", "extractor_sha256", "python", "dependencies")}
            base_language = language
        else:
            current = {k: identity[k] for k in ("extractor", "extractor_sha256", "python", "dependencies")}
            if current != base_identity:
                raise ValueError(
                    f"Archive edition {path.parent.name} was extracted by a different builder; "
                    "merge only identical extractor identities"
                )
            if language != base_language:
                raise ValueError(f"Archive editions mix languages: {path.parent.name}")
        for record in member_records(manifest, path.parent):
            key = record["id"]
            if key in seen_ids:
                raise ValueError(f"Duplicate document {key} in {path.parent.name} and {seen_ids[key]}")
            seen_ids[key] = path.parent.name
            url = record["url"]
            if url in seen_urls:
                raise ValueError(f"Duplicate source URL {url} in {path.parent.name} and {seen_urls[url]}")
            seen_urls[url] = path.parent.name
        members.append({"manifest": manifest, "path": path})
    if not members:
        raise ValueError("A lane edition needs at least one archive edition")
    if output.exists():
        raise FileExistsError(output)

    output.mkdir(parents=True)
    shard_rows = []
    totals = Counter()
    offset = 0
    archive_rows = []
    for member in members:
        manifest = member["manifest"]
        directory = member["path"].parent
        first_shard = len(shard_rows)
        for shard in manifest["shards"]:
            files = {}
            for kind, item in shard["files"].items():
                original = directory / item["name"]
                target = output / item["name"]
                try:
                    os.link(original, target)
                except OSError as error:
                    if error.errno not in (errno.EXDEV,) and getattr(error, "winerror", None) != 17:
                        raise
                    shutil.copyfile(original, target)
                files[kind] = dict(item)
            shard_rows.append(
                dict(
                    start=shard["start"] + offset,
                    end=shard["end"] + offset,
                    counts=dict(shard["counts"]),
                    files=files,
                    archive=manifest["source"]["id"],
                )
            )
            totals.update(shard["counts"])
        archive_rows.append(
            {
                "id": manifest["source"]["id"],
                "edition": manifest["source"]["edition"],
                "filename": manifest["source"]["filename"],
                "bytes": manifest["source"]["bytes"],
                "sha256": manifest["source"]["sha256"],
                "lock_sha256": digest(member["path"]),
                "entries": manifest["total_entries"],
                "shard_first": first_shard,
                "shard_count": len(shard_rows) - first_shard,
            }
        )
        offset += manifest["total_entries"]

    family = {
        "format": FORMAT,
        "lane": lane,
        "complete": True,
        "next_entry": offset,
        "total_entries": offset,
        "identity": dict(base_identity),
        "source": _build_source(
            [m["manifest"] for m in members], lane, edition, collection, scope
        ),
        "archives": archive_rows,
        "archive_metadata": dict(members[0]["manifest"].get("archive_metadata") or {}),
        "totals": dict(totals),
        "shards": shard_rows,
    }
    write_json(output / "manifest.json", family)
    verified_shards(output / "manifest.json")
    print(
        f"Merged {len(members)} archive editions; {totals['documents']:,} documents;"
        f" {len(shard_rows)} shards; {output}",
        flush=True,
    )
    return family


def member_records(manifest: dict, directory: Path):
    """Yield every document of one archive edition without assuming a review step."""
    import gzip

    for shard in manifest["shards"]:
        item = shard["files"]["documents"]
        with gzip.open(directory / item["name"], "rt", encoding="utf-8") as stream:
            for line in stream:
                yield json.loads(line)


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("members", type=Path, nargs="+", help="archive edition manifest.json files")
    parser.add_argument("output", type=Path)
    parser.add_argument("--lane", required=True)
    parser.add_argument("--edition", required=True)
    parser.add_argument("--collection", required=True)
    parser.add_argument("--scope", required=True)
    args = parser.parse_args()
    merge(args.members, args.output, args.lane, args.edition, args.collection, args.scope)
