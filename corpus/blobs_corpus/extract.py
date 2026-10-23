"""Checkpoint ZIM articles in deterministic, independently verifiable shards."""

from collections import Counter
import gzip
import hashlib
import io
import importlib.metadata
import json
import os
import platform
from pathlib import Path
import tempfile
import time

from .acquire import digest, read_lock, verify_source
from .normalize import article, ArticleRedirect, EXTRACTOR_VERSION


def write_json(path: Path, value):
    temporary = path.with_suffix(path.suffix + ".tmp")
    with temporary.open("w", encoding="utf-8", newline="\n") as stream:
        json.dump(value, stream, ensure_ascii=False, sort_keys=True, indent=2)
        stream.write("\n")
        stream.flush()
        os.fsync(stream.fileno())
    try:
        for attempt in range(6):
            try:
                os.replace(temporary, path)
                break
            except PermissionError:
                if attempt == 5:
                    raise
                time.sleep(0.05 * 2**attempt)
    except BaseException:
        temporary.unlink(missing_ok=True)
        raise


def write_lines(path: Path, rows):
    with path.open("wb") as raw:
        with gzip.GzipFile(
            filename="", mode="wb", fileobj=raw, mtime=0, compresslevel=3
        ) as compressed:
            for row in rows:
                compressed.write(
                    (
                        json.dumps(row, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
                        + "\n"
                    ).encode("utf-8")
                )
        raw.flush()
        os.fsync(raw.fileno())


def verified_shards(manifest_path: Path):
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest["format"] != 1:
        raise ValueError("Unsupported extraction manifest")
    expected_start = 0
    for shard in manifest["shards"]:
        if shard["start"] != expected_start or shard["end"] <= shard["start"]:
            raise ValueError("Non-contiguous extraction checkpoints")
        for item in shard["files"].values():
            path = manifest_path.parent / item["name"]
            if path.parent.resolve() != manifest_path.parent.resolve():
                raise ValueError("Shard path escapes the extraction directory")
            if path.stat().st_size != item["bytes"] or digest(path) != item["sha256"]:
                raise ValueError(f"Corrupt or changed shard: {path.name}")
        expected_start = shard["end"]
    if expected_start != manifest["next_entry"]:
        raise ValueError("Checkpoint does not agree with shards")
    return manifest


def extract(lock_path: Path, source: Path, output: Path, max_entries: int | None = None):
    lock = read_lock(lock_path)
    verify_source(source, lock)
    output.mkdir(parents=True, exist_ok=True)
    # An interrupted writer releases its OS file handle, but the small owner file
    # remains for explicit inspection. Never run two checkpoint writers together.
    owner = output / ".extract.lock"
    descriptor = os.open(owner, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
    os.close(descriptor)
    try:
        return _extract(lock, source, output, max_entries)
    finally:
        owner.unlink()


def _extract(lock, source, output, max_entries):
    from libzim.reader import Archive

    started = time.monotonic()
    archive = Archive(source)
    manifest_path = output / "manifest.json"
    code_hash = hashlib.sha256(
        (
            Path(__file__).read_bytes()
            + Path(__file__).with_name("normalize.py").read_bytes()
            + Path(__file__).with_name("records.py").read_bytes()
        )
    ).hexdigest()
    identity = dict(
        source_sha256=lock["sha256"],
        extractor=EXTRACTOR_VERSION,
        extractor_sha256=code_hash,
        python=platform.python_version(),
        dependencies={name: importlib.metadata.version(name) for name in ("libzim", "selectolax")},
        lock_sha256=hashlib.sha256(json.dumps(lock, sort_keys=True).encode()).hexdigest(),
    )
    if manifest_path.exists():
        manifest = verified_shards(manifest_path)
        if manifest["identity"] != identity:
            raise ValueError("Source or extractor changed. Start a new extraction directory.")
    else:
        metadata, binary_metadata = {}, {}
        for key in archive.metadata_keys:
            value = archive.get_metadata(key)
            try:
                metadata[key] = value.decode("utf-8")
            except UnicodeDecodeError:
                binary_metadata[key] = dict(
                    bytes=len(value), sha256=hashlib.sha256(value).hexdigest()
                )
        manifest = dict(
            format=1,
            identity=identity,
            source=lock,
            archive_metadata=metadata,
            archive_binary_metadata=binary_metadata,
            total_entries=archive.entry_count,
            next_entry=0,
            complete=False,
            shards=[],
            totals={},
        )
    stop = (
        min(archive.entry_count, manifest["next_entry"] + max_entries)
        if max_entries
        else archive.entry_count
    )
    totals = Counter(manifest["totals"])
    for start in range(manifest["next_entry"], stop, 5000):
        end = min(start + 5000, stop)
        counts = Counter()
        # Each spool is bounded by a fixed entry count and goes to the output
        # volume, never the operating system's possibly tiny temporary volume.
        with tempfile.TemporaryDirectory(prefix=".extract-", dir=output) as folder:
            paths = {
                kind: Path(folder) / f"{start:08d}-{end:08d}.{kind}.jsonl.gz"
                for kind in ("documents", "aliases", "rejected")
            }
            from contextlib import ExitStack

            with ExitStack() as stack:
                streams = {}
                for kind, path in paths.items():
                    raw = stack.enter_context(path.open("wb"))
                    compressed = stack.enter_context(
                        gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0, compresslevel=3)
                    )
                    streams[kind] = stack.enter_context(
                        io.TextIOWrapper(compressed, encoding="utf-8", newline="\n")
                    )

                def emit(kind, value):
                    streams[kind].write(
                        json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
                        + "\n"
                    )

                for index in range(start, end):
                    # This enumeration method is exposed in the pinned libzim
                    # reader stub; no random sampling or ZIM full-text query.
                    entry = archive._get_entry_by_id(index)
                    if entry.is_redirect:
                        # A ZIM root entry is a redirect with no path and no title.
                        # It can name nothing, and the name index rejects it.
                        if entry.path and entry.title:
                            emit(
                                "aliases",
                                dict(
                                    title=entry.title,
                                    path=entry.path,
                                    target=entry.get_redirect_entry().path,
                                ),
                            )
                            counts["redirects"] += 1
                        else:
                            counts["unnamed_redirects"] += 1
                        continue
                    item = entry.get_item()
                    if item.mimetype.split(";")[0] != "text/html":
                        counts["non_article_assets"] += 1
                        continue
                    try:
                        if item.size > 16 * 1024**2:
                            raise ValueError("HTML exceeds 16 MiB input limit")
                        document = article(bytes(item.content), entry.title, entry.path, lock)
                        emit("documents", document)
                        counts["documents"] += 1
                        counts["text_characters"] += len(document["body"])
                        counts["text_utf8_bytes"] += len(document["body"].encode())
                        counts["with_headings"] += int(
                            "\n\n==" in document["body"] or document["body"].startswith("==")
                        )
                        counts["short_under_1000_chars"] += int(len(document["body"]) < 1000)
                        counts["long_over_100000_chars"] += int(len(document["body"]) > 100000)
                        counts["attributed"] += 1
                    except ArticleRedirect as redirect:
                        emit(
                            "aliases",
                            dict(title=entry.title, path=entry.path, target=redirect.target),
                        )
                        counts["html_redirects"] += 1
                    except (ValueError, UnicodeError) as error:
                        emit(
                            "rejected",
                            dict(
                                index=index, path=entry.path, title=entry.title, reason=str(error)
                            ),
                        )
                        counts["rejected"] += 1
            files = {}
            for kind, path in paths.items():
                # Closing Python buffers is insufficient across a host crash.
                # Flush each completed member before publishing its checkpoint.
                with path.open("r+b") as durable:
                    os.fsync(durable.fileno())
                files[kind] = dict(name=path.name, bytes=path.stat().st_size, sha256=digest(path))
                # Names encode the immutable index range. Files unreferenced by
                # the manifest are incomplete checkpoints and may be regenerated.
                os.replace(path, output / path.name)
        totals.update(counts)
        manifest["shards"].append(dict(start=start, end=end, counts=dict(counts), files=files))
        manifest.update(next_entry=end, complete=end == archive.entry_count, totals=dict(totals))
        write_json(manifest_path, manifest)
        print(
            f"Extracted entries {end:,}/{archive.entry_count:,}; {totals['documents']:,} documents, "
            f"{totals['rejected']:,} rejected ({time.monotonic() - started:.1f}s this run)",
            flush=True,
        )
    return manifest_path


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("lock", type=Path)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--max-entries", type=int)
    args = parser.parse_args()
    if args.max_entries is not None and args.max_entries < 1:
        parser.error("--max-entries must be positive")
    extract(args.lock, args.source, args.output, args.max_entries)
