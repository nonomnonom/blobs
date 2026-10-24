"""Remove Kiwix support pages from a verified extraction, preserving its lineage."""

from collections import Counter
import copy
import hashlib
import os
from pathlib import Path
import re
import tempfile

from .acquire import digest
from .extract import verified_shards, write_json, write_lines
from .records import documents

# The review policy is path-based and language-agnostic, so it is approved per
# source project, not per individual archive edition.
SUPPORTED_PROJECTS = {"wikipedia", "wikisource", "wikivoyage", "stackexchange"}

# Stack Exchange ZIMs also carry the site's chrome: member profiles, tag
# directories and paged question listings. Only a question page is a post.
QUESTION_PATH = re.compile(r"questions/\d+/")

# Wikisource carries a work's text as one page plus subpages. Author: pages
# are bibliographic stubs, Portal: pages are subject navigation, and the main
# page is site chrome, so none of them is a primary text. Version and
# disambiguation pages only link to the works; they are navigation too, and
# they outrank the works they list whenever a question names a work.


def navigation_page(body: str):
    head = body[:600]
    if " may refer to:" in body[:300]:
        return "Disambiguation page, not a primary text"
    if "Versions of " in head and "include:" in head:
        return "Version index page, not a primary text"
    return None


def project(source_id: str) -> str:
    return str(source_id).split("-")[0]


def exclusion(document, source_project: str = ""):
    path = document.get("provenance", {}).get("entry_path", "")
    if source_project == "stackexchange" and not QUESTION_PATH.match(path):
        if path.startswith("users"):
            return "Community member profile, not a question"
        if path.startswith("tags"):
            return "Tag directory page, not a question"
        return "Site navigation or question listing, not a question"
    if source_project == "wikisource":
        if path.startswith("Author:"):
            return "Author metadata page, not a primary text"
        if path.startswith("Portal:"):
            return "Subject portal navigation, not a primary text"
        if path in ("Main_Page", "Main Page"):
            return "Archive navigation page, not a primary text"
        if not path.startswith(("_assets_/", "_mw_/")) and path != "index":
            return navigation_page(document.get("body", ""))
    if path.startswith("_assets_/"):
        return "Bundled support asset, not a source article"
    if path.startswith("_mw_/"):
        return "MediaWiki support content, not a source article"
    if path == "index":
        return "Archive navigation page, not a source article"
    return None


def curate(source: Path, output: Path):
    original = verified_shards(source)
    if not original["complete"]:
        raise ValueError("Finish extraction before review")
    reviewed_projects = [entry["id"] for entry in original.get("archives", [])] or [
        original["source"]["id"]
    ]
    if any(project(name) not in SUPPORTED_PROJECTS for name in reviewed_projects) or original.get(
        "curation"
    ):
        raise ValueError(
            "This review policy applies once to a supported raw Kiwix article extraction"
        )
    if output.exists():
        raise FileExistsError(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    manifest = copy.deepcopy(original)
    excluded, totals = [], Counter()
    # The portfolio rule forbids shipping the same body twice. Distinct source
    # pages can still normalize to identical text, so the first record in
    # manifest order keeps the body and later copies are recorded as exclusions.
    seen_bodies = set()
    with tempfile.TemporaryDirectory(prefix=".curate-", dir=output.parent) as temporary:
        staged = Path(temporary) / "reviewed"
        staged.mkdir()
        for shard in manifest["shards"]:
            info = shard["files"]["documents"]
            path = source.parent / info["name"]
            counts = Counter(shard["counts"])
            changed = False
            dropped = set()
            archive_project = project(shard.get("archive") or original["source"]["id"])
            for doc in documents(path):
                reason = exclusion(doc, archive_project)
                counter = {
                    "Unanswered question, not an answer to ship": "excluded_unanswered",
                    "Disambiguation page, not a primary text": "excluded_disambiguation_pages",
                    "Version index page, not a primary text": "excluded_version_pages",
                }.get(reason, "excluded_non_articles")
                if reason is None and archive_project == "stackexchange":
                    # A question page with no answers records a problem, not
                    # knowledge; shipping it returns an unanswered query.
                    normalization = doc.get("provenance", {}).get("normalization") or {}
                    if normalization.get("answers") == 0:
                        reason = "Unanswered question, not an answer to ship"
                        counter = "excluded_unanswered"
                if reason is None:
                    body_sha256 = doc.get("provenance", {}).get("body_sha256")
                    if body_sha256 and body_sha256 in seen_bodies:
                        reason = "Duplicate body text of an earlier record"
                        counter = "excluded_duplicate_bodies"
                    elif body_sha256:
                        seen_bodies.add(body_sha256)
                if reason is None:
                    continue
                changed = True
                dropped.add(doc["id"])
                excluded.append(
                    dict(
                        id=doc["id"],
                        title=doc["title"],
                        entry_path=doc["provenance"]["entry_path"],
                        reason=reason,
                    )
                )
                body = doc["body"]
                removed = dict(
                    documents=1,
                    attributed=1,
                    text_characters=len(body),
                    text_utf8_bytes=len(body.encode()),
                    with_headings=int("\n\n==" in body or body.startswith("==")),
                    short_under_1000_chars=int(len(body) < 1000),
                    long_over_100000_chars=int(len(body) > 100000),
                )
                counts.subtract({key: value for key, value in removed.items() if key in counts})
                counts[counter] += 1
            for kind, item in shard["files"].items():
                target = staged / item["name"]
                if kind == "documents" and changed:
                    write_lines(
                        target,
                        (doc for doc in documents(path) if doc["id"] not in dropped),
                    )
                    item.update(bytes=target.stat().st_size, sha256=digest(target))
                else:
                    os.link(source.parent / item["name"], target)
            shard["counts"] = dict(counts)
            totals.update(counts)
        manifest["totals"] = dict(totals)
        manifest["curation"] = dict(
            policy="Kiwix article-only review v3",
            source_id=original["source"]["id"],
            policy_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
            input_manifest_sha256=digest(source),
            input_totals=original["totals"],
            deduplication="exact body_sha256 within the reviewed edition; first record in manifest order wins",
            excluded_documents=excluded,
        )
        write_json(staged / "manifest.json", manifest)
        verified_shards(staged / "manifest.json")
        os.rename(staged, output)
    print(
        f"Reviewed {totals['documents']:,} articles; excluded {len(excluded)} pages "
        f"({totals.get('excluded_duplicate_bodies', 0):,} duplicate bodies); {output}",
        flush=True,
    )
    return manifest


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    curate(args.source, args.output)
