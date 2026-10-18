"""Acquire and normalize a small, pinned set of official web reference pages."""
import argparse
from datetime import date, datetime
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import tempfile
import urllib.error
import urllib.parse
import urllib.request

from selectolax.lexbor import LexborHTMLParser

from .acquire import checked_url
from .normalize import _blocks, clean
from .records import validate_document

MAX_SOURCE_BYTES = 5_000_000
MAX_SOURCES = 40
EXTRACTOR_VERSION = "1"


def read_lock(path: Path) -> dict:
    lock = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(lock, dict):
        raise ValueError("Pinned web-source lock must be an object")
    sources = lock.get("sources")
    if (
        lock.get("format") != 1
        or not re.fullmatch(r"[a-z0-9-]{1,64}", lock.get("id", ""))
        or not re.fullmatch(r"[0-9A-Za-z.-]{1,40}", lock.get("edition", ""))
        or not isinstance(sources, list)
        or not 1 <= len(sources) <= MAX_SOURCES
    ):
        raise ValueError("Invalid pinned web-source lock")

    ids = set()
    for source in sources:
        if not isinstance(source, dict):
            raise ValueError("Invalid web source entry")
        source_id = source.get("id")
        if not isinstance(source_id, str) or not re.fullmatch(r"[a-z0-9-]{1,64}", source_id) or source_id in ids:
            raise ValueError("Invalid or duplicate web source ID")
        ids.add(source_id)
        url = checked_url(source.get("url", ""), [source.get("host", "")])
        parsed = urllib.parse.urlsplit(url)
        if parsed.hostname != source["host"] or parsed.query or parsed.fragment:
            raise ValueError(f"Web source URL must be a stable, same-host URL: {source_id}")
        if (
            not isinstance(source.get("body_sha256"), str)
            or not re.fullmatch(r"[a-f0-9]{64}", source["body_sha256"])
            or not isinstance(source.get("title"), str)
            or not source["title"].strip()
            or not isinstance(source.get("updated"), str)
            or not source["updated"].strip()
            or not isinstance(source.get("selector"), str)
            or not source["selector"].startswith("#")
        ):
            raise ValueError(f"Invalid pinned web source metadata: {source_id}")
        try:
            if date.fromisoformat(source["updated"]).isoformat() != source["updated"]:
                raise ValueError
        except ValueError as error:
            raise ValueError(f"Invalid page update date: {source_id}") from error
    for field in ("publisher", "license", "saved", "collection", "origin"):
        if not isinstance(lock.get(field), str) or not lock[field].strip():
            raise ValueError(f"Missing web-corpus metadata: {field}")
    return lock


class SameHostRedirect(urllib.request.HTTPRedirectHandler):
    def __init__(self, host: str):
        self.host = host

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        checked_url(newurl, [self.host])
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def acquire(lock_path: Path, directory: Path) -> list[Path]:
    lock = read_lock(lock_path)
    directory.mkdir(parents=True, exist_ok=True)
    results = []
    for source in lock["sources"]:
        target = directory / f"{source['id']}.html"
        if target.exists():
            _source_text(target.read_bytes(), source)
            results.append(target)
            continue
        if shutil.disk_usage(directory).free < 1024**3 + 1024 * 1024:
            raise OSError("Keep at least 1 GiB free while acquiring source snapshots")
        opener = urllib.request.build_opener(SameHostRedirect(source["host"]))
        request = urllib.request.Request(
            source["url"],
            headers={
                "User-Agent": "BlobsCorpus/0.1 (reproducible offline library)",
                "Accept-Encoding": "identity",
            },
        )
        with opener.open(request, timeout=45) as response:
            checked_url(response.geturl(), [source["host"]])
            content_type = response.headers.get("Content-Type", "").lower()
            if not content_type.startswith("text/html"):
                raise ValueError(f"Expected HTML source page: {source['id']}")
            data = response.read(MAX_SOURCE_BYTES + 1)
        if len(data) > MAX_SOURCE_BYTES:
            raise ValueError(f"Remote page exceeds the size limit: {source['id']}")
        _source_text(data, source)
        partial = target.with_suffix(".html.partial")
        try:
            with partial.open("xb") as stream:
                stream.write(data)
                stream.flush()
                os.fsync(stream.fileno())
            os.link(partial, target)
        finally:
            partial.unlink(missing_ok=True)
        results.append(target)
    return results


def _remove_furniture(root):
    for node in root.css(
        "script,style,nav,footer,form,figure,img,svg,picture,iframe,video,audio,"
        "[role~='navigation'],.RelatedGrid,[class*='RelatedGrid']"
    ):
        node.decompose()
    for node in root.css("h4"):
        links = node.css("a")
        if len(links) > 1 and all("#" in link.attributes.get("href", "") for link in links):
            node.decompose()


def _source_text(raw: bytes, source: dict) -> tuple[str, str]:
    if len(raw) > MAX_SOURCE_BYTES:
        raise ValueError(f"Source page exceeds the size limit: {source['id']}")
    tree = LexborHTMLParser(raw.decode("utf-8"))
    title_node = tree.css_first("h1")
    title = clean(title_node.text()) if title_node else ""
    if title != source["title"]:
        raise ValueError(f"Unexpected source title for {source['id']}: {title!r}")
    page_text = tree.body.text(separator=" ", strip=True) if tree.body else ""
    update_dates = re.findall(r"Last updated:\s*([A-Za-z]+\s+\d{1,2},\s+\d{4})", page_text)
    if len(update_dates) != 1:
        raise ValueError(f"Expected one page update date: {source['id']}")
    updated = datetime.strptime(update_dates[0], "%B %d, %Y").date().isoformat()
    if updated != source["updated"]:
        raise ValueError(f"Page update date changed: {source['id']} ({updated})")
    root = tree.css_first(source["selector"])
    if root is None:
        raise ValueError(f"Missing locked article body selector: {source['id']}")
    _remove_furniture(root)
    body = "\n\n".join(block for block in _blocks(root) if block.strip())
    if len(body) < 300:
        raise ValueError(f"Normalized source is unexpectedly short: {source['id']}")
    body_sha256 = hashlib.sha256(body.encode("utf-8")).hexdigest()
    if body_sha256 != source["body_sha256"]:
        raise ValueError(f"Reviewed source text changed: {source['id']}")
    return title, body


def extract(lock_path: Path, snapshots: Path, output: Path) -> Path:
    from .extract import write_lines

    lock = read_lock(lock_path)
    if output.exists():
        raise FileExistsError(output)
    rows = []
    for source in lock["sources"]:
        path = snapshots / f"{source['id']}.html"
        if path.parent.resolve() != snapshots.resolve():
            raise ValueError("Snapshot path escapes its source directory")
        raw = path.read_bytes()
        title, body = _source_text(raw, source)
        url = source["url"]
        item = dict(
            id=f"{lock['id']}-{source['id']}",
            title=title,
            publisher=lock["publisher"],
            url=url,
            license=lock["license"],
            updated=source["updated"],
            saved=lock["saved"],
            collection=lock["collection"],
            body=body,
            provenance=dict(
                source_id=lock["id"],
                edition=lock["edition"],
                source_content_sha256=source["body_sha256"],
                body_sha256=hashlib.sha256(body.encode("utf-8")).hexdigest(),
                source_url=url,
                source_host=source["host"],
                extractor=f"blobs_corpus.web_pages/{EXTRACTOR_VERSION}",
                extractor_sha256=hashlib.sha256(
                    Path(__file__).read_bytes()
                    + Path(__file__).with_name("normalize.py").read_bytes()
                    + Path(__file__).with_name("records.py").read_bytes()
                ).hexdigest(),
                selector=source["selector"],
            ),
        )
        validate_document(item)
        rows.append(item)
    output.parent.mkdir(parents=True, exist_ok=True)
    write_lines(output, rows)
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    acquire_parser = subparsers.add_parser("acquire")
    acquire_parser.add_argument("lock", type=Path)
    acquire_parser.add_argument("directory", type=Path)
    extract_parser = subparsers.add_parser("extract")
    extract_parser.add_argument("lock", type=Path)
    extract_parser.add_argument("snapshots", type=Path)
    extract_parser.add_argument("output", type=Path)
    args = parser.parse_args()
    if args.command == "acquire":
        for path in acquire(args.lock, args.directory):
            print(f"Verified {path}", flush=True)
    else:
        print(f"Normalized records written to {extract(args.lock, args.snapshots, args.output)}", flush=True)


if __name__ == "__main__":
    main()
