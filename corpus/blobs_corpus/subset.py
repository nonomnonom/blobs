"""Select explicitly reviewed articles from a pinned record collection."""

import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import re
import tempfile

from .acquire import digest
from .extract import write_lines
from .records import documents


def subset(lock_path: Path, source: Path, output: Path) -> Path:
    lock = json.loads(lock_path.read_text(encoding="utf-8"))
    if (
        lock.get("format") != 1
        or not re.fullmatch(r"[a-z0-9-]{1,64}", lock.get("id", ""))
        or not isinstance(lock.get("collection"), str)
        or not 1 <= len(lock["collection"]) <= 200
        or not re.fullmatch(r"[a-f0-9]{64}", lock.get("input_sha256", ""))
        or not isinstance(lock.get("documents"), list)
        or not 1 <= len(lock["documents"]) <= 500
    ):
        raise ValueError("Invalid article selection lock")
    selected = {}
    for row in lock["documents"]:
        if (
            not isinstance(row, dict)
            or not re.fullmatch(r"[a-zA-Z0-9_-]{1,100}", row.get("id", ""))
            or row["id"] in selected
            or not isinstance(row.get("title"), str)
            or not 1 <= len(row["title"]) <= 300
            or not isinstance(row.get("url"), str)
            or not row["url"].startswith("https://")
            or not re.fullmatch(r"[a-f0-9]{64}", row.get("body_sha256", ""))
        ):
            raise ValueError("Invalid or duplicate selected article")
        selected[row["id"]] = row
    if digest(source) != lock["input_sha256"]:
        raise ValueError("Input records do not match the selection lock")
    if output.exists():
        raise FileExistsError(output)
    found = {}
    lock_digest = digest(lock_path)
    for document in documents(source):
        expected = selected.get(document["id"])
        if expected is None:
            continue
        if (
            document["id"] in found
            or document["title"] != expected["title"]
            or document["url"] != expected["url"]
            or hashlib.sha256(document["body"].encode("utf-8")).hexdigest()
            != expected["body_sha256"]
        ):
            raise ValueError(f"Selected article changed or repeated: {document['id']}")
        item = copy.deepcopy(document)
        item["collection"] = lock["collection"]
        item.setdefault("provenance", {})["selection"] = dict(
            id=lock["id"],
            lock_sha256=lock_digest,
            input_sha256=lock["input_sha256"],
        )
        found[document["id"]] = item
    if found.keys() != selected.keys():
        raise ValueError("Selected articles are missing from the input records")
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".subset-", dir=output.parent) as temporary:
        staged = Path(temporary) / "documents.jsonl.gz"
        write_lines(staged, (found[key] for key in selected))
        os.link(staged, output)
    return output


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("lock", type=Path)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    print(subset(args.lock, args.source, args.output))
