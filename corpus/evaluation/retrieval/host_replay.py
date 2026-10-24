"""Replay one frozen Android retrieval policy for full-corpus diagnosis, not phone benchmarks."""

import argparse
from collections import Counter
from contextlib import closing
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sqlite3
import tempfile
import time
import zipfile

from blobs_corpus.names import name_key


# Any retrieval-policy change requires a new native run and a reviewed replay.
# Single-library parity compares exact passage identities and text hashes.
LIBRARY_STORE_SHA256 = "6c44e154b496ebe2f601c74583fd2f3f3224650dff77c60a64eab4acf122354a"
QUERY_SHA256 = "d2beec91da15db229894e0aabdf944678f03e60d1a6b3cdf3e156f37c7296625"
STOP_WORDS = {
    "the",
    "and",
    "or",
    "not",
    "for",
    "with",
    "that",
    "this",
    "what",
    "how",
    "why",
    "are",
    "does",
    "can",
    "you",
    "about",
    "from",
    "which",
    "have",
    "would",
    "their",
    "they",
    "its",
    "these",
    "those",
    "compare",
    "explain",
    "between",
    "difference",
    "versus",
    "could",
    "do",
    "did",
    "be",
    "it",
    "into",
    "also",
    "tell",
    "please",
    "me",
    "more",
    "than",
    "when",
    "where",
    "who",
    "was",
    "were",
    "has",
    "had",
    "is",
    "of",
    "to",
    "by",
    "on",
    "in",
    "an",
    "as",
    "at",
    "if",
    "we",
    "us",
    "our",
    "describe",
    "guide",
    "list",
    "should",
    "know",
    "want",
    "need",
}
TITLE_INTENT_STOP_WORDS = STOP_WORDS | {
    "i",
    "should",
    "know",
    "before",
    "visiting",
    "visit",
    "around",
}
TITLE_NOISE_WORDS = {"city", "town"}
LOW_SPECIFICITY_NUMBER_WORDS = {
    "zero",
    "one",
    "two",
    "three",
    "four",
    "five",
    "six",
    "seven",
    "eight",
    "nine",
    "ten",
    "eleven",
    "twelve",
    "thirteen",
    "fourteen",
    "fifteen",
    "sixteen",
    "seventeen",
    "eighteen",
    "nineteen",
    "twenty",
    "hundred",
    "thousand",
    "times",
    "twice",
}
NON_TOPICAL_WORDS = (
    TITLE_INTENT_STOP_WORDS | TITLE_NOISE_WORDS | {"best"} | LOW_SPECIFICITY_NUMBER_WORDS
)
RESPONSE_LENGTH_INSTRUCTION = re.compile(
    r"(?:^|(?<=[.!?\n]))\s*(?:please\s+)?"
    r"(?:answer|respond)\s+in\s+(?:[0-9]+|one|two|three)\s+"
    r"(?:(?:short|brief|concise)\s+)?(?:sentences?|paragraphs?|words?)\s*"
    r"(?:[.!?:](?=\s|$)|$)",
    re.IGNORECASE,
)


def content_question(question):
    return RESPONSE_LENGTH_INSTRUCTION.sub("", question).strip()


def sha(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def unpack(package, target):
    with zipfile.ZipFile(package) as archive:
        if archive.namelist() != ["manifest.json", "library.sqlite"]:
            raise ValueError("Expected one library payload after its manifest")
        if archive.getinfo("manifest.json").file_size > 65536:
            raise ValueError("Manifest exceeds the native bound")
        manifest = json.loads(archive.read("manifest.json"))
        if manifest["kind"] != "library" or not 0 < manifest["bytes"] <= 6 * 1024**3:
            raise ValueError("Invalid library manifest")
        if archive.getinfo("library.sqlite").file_size != manifest["bytes"]:
            raise ValueError("Payload length differs from manifest")
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists():
            if target.stat().st_size != manifest["bytes"] or sha(target) != manifest["sha256"]:
                raise ValueError("Existing review database differs from this package")
            return manifest
        if shutil.disk_usage(target.parent).free < manifest["bytes"] + 1024**3:
            raise OSError("Allow the complete database plus 1 GiB free space")
        with tempfile.TemporaryDirectory(prefix=".unpack-", dir=target.parent) as temporary:
            staged = Path(temporary) / "library.sqlite"
            digest, count = hashlib.sha256(), 0
            with archive.open("library.sqlite") as source, staged.open("xb") as output:
                while block := source.read(1024 * 1024):
                    count += len(block)
                    if count > manifest["bytes"]:
                        raise ValueError("Payload exceeds its declared length")
                    digest.update(block)
                    output.write(block)
                output.flush()
                os.fsync(output.fileno())
            if count != manifest["bytes"] or digest.hexdigest() != manifest["sha256"]:
                raise ValueError("Payload verification failed")
            os.link(staged, target)
    return manifest


def verify_report_library(case, manifest):
    """Bind native retrieval evidence to the exact package being replayed."""
    if case.get("library") != manifest:
        raise ValueError("Native retrieval evidence uses a different library package")


def title_terms(question):
    return {
        word
        for word in name_key(question).split()
        if len(word) >= 2 and word not in STOP_WORDS and word not in TITLE_NOISE_WORDS
    }


def title_overlap(question_terms, title):
    title_words = name_key(title).split()
    return sum(
        1
        for term in question_terms
        if any(
            word == term
            or (len(term) >= 3 and word.startswith(term) and len(word) - len(term) <= 3)
            for word in title_words
        )
    )


def topic_terms(question, subjects):
    if not subjects:
        return set()
    subject_terms = set().union(*(title_terms(title) for title in subjects))
    return title_terms(question) - subject_terms - TITLE_INTENT_STOP_WORDS - {"best"}


def evidence_overlap(terms, title, section, text):
    topical_terms = terms - NON_TOPICAL_WORDS
    words = set(re.findall(r"[^\W_]+", f"{title} {section} {text}".lower()))
    return len(topical_terms & words)


def minimum_evidence_overlap(question_terms):
    count = len(question_terms - NON_TOPICAL_WORDS)
    return None if count == 0 else 1 if count == 1 else 2


def is_broad_title_question(question, title):
    title_words = set(name_key(title).split())
    question_terms = [
        word
        for word in name_key(question).split()
        if len(word) >= 2 and word not in TITLE_INTENT_STOP_WORDS
    ]
    return bool(question_terms) and all(word in title_words for word in question_terms)


def is_direct_title_match(question, title):
    if is_broad_title_question(question, title):
        return True
    normalized_question = f" {name_key(question)} "
    normalized_title = name_key(title)
    title_words = normalized_title.split()
    if len(title_words) > 1:
        return f" {normalized_title} " in normalized_question
    title_word = title_words[0] if title_words else ""
    return any(
        match.start() > 0 and match.group()[0].isupper() and name_key(match.group()) == title_word
        for match in re.finditer(r"[^\W_]+", question, re.UNICODE)
    )


def retrieve(database, manifest, query, *, question="", priority_ids=(), broad_priority_ids=None):
    question = content_question(question)
    route_ids = list(dict.fromkeys(priority_ids))
    title_priority = set(route_ids if broad_priority_ids is None else broad_priority_ids)
    with closing(sqlite3.connect(database.as_uri() + "?mode=ro", uri=True)) as db:
        db.execute("PRAGMA trusted_schema=OFF")
        db.execute("PRAGMA query_only=ON")
        schema = db.execute("PRAGMA user_version").fetchone()[0]
        if schema not in (2, 3):
            raise ValueError(f"Unsupported library schema: {schema}")
        db.row_factory = sqlite3.Row
        if schema == 2:
            statement = """SELECT d.id,d.title,d.publisher,d.url,d.license,d.updated,d.saved,
                p.section,p.body,p.title AS opening_title,p.id AS passage_id,rank AS score
                FROM passages_fts JOIN passages p ON p.id=passages_fts.rowid
                JOIN documents d ON d.id=p.doc_id WHERE passages_fts MATCH ?
                AND (p.title!='' OR lower(trim(p.section)) NOT IN
                  ('references','external links','see also','further reading','bibliography'))
                ORDER BY rank LIMIT 24"""
        else:
            statement = """SELECT d.id,d.title,d.publisher,d.url,d.license,d.updated,d.saved,
                p.section,substr(d.body,s.start+1,s.end-s.start) AS body,p.title AS opening_title,
                p.id AS passage_id,rank AS score
                FROM passages_fts JOIN passages p ON p.id=passages_fts.rowid
                JOIN passage_spans s ON s.passage_id=p.id
                JOIN documents d ON d.id=p.doc_id WHERE passages_fts MATCH ?
                AND (p.title!='' OR lower(trim(p.section)) NOT IN
                  ('references','external links','see also','further reading','bibliography'))
                ORDER BY rank LIMIT 24"""
        ranked = [dict(row) for row in db.execute(statement, (query,))] if query else []
        subjects = (
            [
                row[0]
                for row in db.execute(
                    "SELECT title FROM documents WHERE id IN ("
                    + ",".join("?" for _ in route_ids)
                    + ")",
                    route_ids,
                )
            ]
            if route_ids
            else []
        )
        question_terms = title_terms(question)
        focus_terms = topic_terms(question, subjects)
        candidates = []
        for order, row in enumerate(ranked):
            title = row["title"]
            subject = next(
                (
                    name
                    for name in subjects
                    if title == name or title.startswith(name + "/") or title.endswith(" " + name)
                ),
                None,
            )
            row["subject_match"] = subject
            row["named_subject"] = name_key(subject) if subject else None
            row["title_priority"] = bool(row["opening_title"]) and row["id"] in title_priority
            row["direct_title_match"] = row["id"] in route_ids
            row["title_overlap"] = title_overlap(question_terms, title)
            row["topic_overlap"] = evidence_overlap(focus_terms, title, row["section"], row["body"])
            row["evidence_overlap"] = evidence_overlap(
                question_terms, title, row["section"], row["body"]
            )
            row["order"] = order
            candidates.append(row)
    return select_evidence(candidates, manifest, question=question), candidates


def direct_title_ids(database, question, titles, *, broad_only=False):
    """Return exact canonical-title routes selected by the Android name matcher."""
    if not titles:
        return []
    question = content_question(question)
    words = name_key(question).split()[:40]
    candidates = []
    for size in range(min(6, len(words)), 0, -1):
        for start in range(len(words) - size + 1):
            phrase = words[start : start + size]
            key = " ".join(phrase)
            if 2 <= len(key) <= 600 and any(
                len(word) >= 2 and word not in STOP_WORDS for word in phrase
            ):
                candidates.append((key, set(range(start, start + size))))
    keys = list(dict.fromkeys(key for key, _ in candidates))
    if not keys:
        return []
    with closing(sqlite3.connect(database.resolve().as_uri() + "?mode=ro", uri=True)) as db:
        exists = db.execute(
            "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='document_names'"
        ).fetchone()[0]
        if not exists:
            return []
        rows = db.execute(
            """SELECT n.name,min(d.title),min(d.id) FROM document_names n
               JOIN documents d ON d.id=n.doc_id WHERE n.name IN ("""
            + ",".join("?" for _ in keys)
            + """) GROUP BY n.name HAVING count(*)=1""",
            keys,
        ).fetchall()
    matches = {key: (title, doc_id) for key, title, doc_id in rows}
    title_set = set(titles)
    occupied, ids = set(), []
    for key, positions in candidates:
        match = matches.get(key)
        if match is None or match[0] not in title_set or occupied.intersection(positions):
            continue
        occupied.update(positions)
        title = match[0]
        if match[1] not in ids:
            broad = is_broad_title_question(question, title)
            direct = is_direct_title_match(question, key) or is_direct_title_match(question, title)
            if broad if broad_only else direct:
                ids.append(match[1])
    return ids


def retrieval_spec(database, question):
    """Mirror LibraryStore's unique source-name routing and FTS query construction."""
    import sqlite3

    question = content_question(question)
    words = name_key(question).split()[:40]
    candidates = []
    for size in range(min(6, len(words)), 0, -1):
        for start in range(len(words) - size + 1):
            phrase = words[start : start + size]
            key = " ".join(phrase)
            if 2 <= len(key) <= 600 and any(
                len(word) >= 2 and word not in STOP_WORDS for word in phrase
            ):
                candidates.append((key, set(range(start, start + size))))
    with closing(sqlite3.connect(database.as_uri() + "?mode=ro", uri=True)) as db:
        names = list(dict.fromkeys(key for key, _ in candidates))
        if names:
            placeholders = ",".join("?" for _ in names)
            rows = db.execute(
                f"""SELECT n.name,min(d.title),min(d.id) FROM document_names n
                    JOIN documents d ON d.id=n.doc_id WHERE n.name IN ({placeholders})
                    GROUP BY n.name HAVING count(*)=1""",
                names,
            ).fetchall()
            matches = {row[0]: (row[1], row[2]) for row in rows}
        else:
            matches = {}
    occupied, titles, matched_ids = set(), [], set()
    for key, positions in candidates:
        if key not in matches or occupied.intersection(positions):
            continue
        occupied.update(positions)
        title, doc_id = matches[key]
        if doc_id in matched_ids:
            continue
        matched_ids.add(doc_id)
        titles.append(title)
    words = [word for word in name_key(question).split() if len(word) >= 2]
    terms = list(dict.fromkeys(word for word in words if word not in STOP_WORDS))[:20]
    query = [f'"{term}"' for term in terms]
    phrase_terms = list(
        dict.fromkeys(
            f'"{left} {right}"'
            for left, right in zip(words, words[1:])
            if left not in STOP_WORDS and right not in STOP_WORDS
        )
    )[:20]
    query.extend(phrase_terms)
    for title in titles[:4]:
        phrase = name_key(title)
        if phrase:
            query.append(f'title:"{phrase}"')
    return " OR ".join(dict.fromkeys(query)), titles


def retrieval_query(database, question):
    return retrieval_spec(database, question)[0]


def select_evidence(candidates, manifest, *, question=""):
    """Apply LibraryStore.selectEvidence: ordering, focus, relevance and budgets."""
    terms = title_terms(content_question(question)) if question else set()
    ranked = sorted(
        candidates,
        key=lambda row: (
            not row.get("title_priority", False),
            row.get("subject_match") is None,
            -row.get("topic_overlap", 0),
            -row.get("evidence_overlap", 0),
            -row.get("title_overlap", 0),
            not row.get("direct_title_match", False),
            row.get("order", 0),
        ),
    )
    direct_subjects = [row for row in ranked if row.get("subject_match")]
    broad_titles = list(dict.fromkeys(row["title"] for row in ranked if row.get("title_priority")))
    if direct_subjects:
        focused = direct_subjects
    elif not broad_titles:
        focused = ranked
    else:
        focused = [
            row
            for row in ranked
            if any(
                row["title"] == title or row["title"].startswith(title + "/")
                for title in broad_titles
            )
        ]
    minimum_overlap = minimum_evidence_overlap(terms)
    relevant = [
        row
        for row in focused
        if row.get("title_priority")
        or row.get("direct_title_match")
        or row.get("subject_match")
        or minimum_overlap is None
        or row.get("evidence_overlap", 0) >= minimum_overlap
    ]
    unique, seen = [], set()
    for row in relevant:
        key = (row["url"], row["id"], row["title"], row["section"], row["body"])
        if key in seen:
            continue
        seen.add(key)
        unique.append(row)
    edition = f"{manifest['title']} · {manifest['version']} · {manifest['sha256']}"
    prefix = f"{manifest['id']}-{manifest['version']}-{manifest['sha256'][:16]}"
    selected, passages, per_document, characters = [], set(), Counter(), 0

    def document(row):
        return (row["url"], row["id"], row["title"])

    def append(row):
        nonlocal characters
        evidence_id = f"{prefix}/{row['id']}"
        cost = min(len(row["body"].encode("utf-16-le")) // 2, 2000)
        key = document(row)
        if (
            len(selected) < 6
            and per_document[key] < 3
            and characters + cost <= 6000
            and (evidence_id, row["passage_id"]) not in passages
        ):
            evidence = {
                name: row[name]
                for name in ("title", "publisher", "url", "license", "updated", "saved", "section")
            }
            evidence.update(
                id=evidence_id,
                text=row["body"],
                edition=edition,
                passageId=row["passage_id"],
            )
            selected.append(evidence)
            passages.add((evidence_id, row["passage_id"]))
            per_document[key] += 1
            characters += cost

    subject_rows = [row for row in unique if row.get("subject_match")]
    subjects_seen = set()
    for row in subject_rows:
        if row["subject_match"] in subjects_seen:
            continue
        subjects_seen.add(row["subject_match"])
        append(row)
    groups = {}
    for row in subject_rows:
        groups.setdefault(document(row), []).append(row)
    for group in sorted(
        groups.values(), key=lambda rows: -max(row.get("topic_overlap", 0) for row in rows)
    ):
        for row in group[:3]:
            append(row)
    documents_seen = set()
    for row in unique:
        if document(row) in documents_seen:
            continue
        documents_seen.add(document(row))
        append(row)
    for row in unique:
        append(row)
    return selected


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--native-report", type=Path, required=True)
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--work-directory", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    sources = root.parent / "android/app/src/main/kotlin/dev/blobs/app/data/library"
    if (
        sha(sources / "LibraryStore.kt") != LIBRARY_STORE_SHA256
        or sha(sources / "RetrievalQuery.kt") != QUERY_SHA256
    ):
        raise ValueError("Native retrieval code changed; review this frozen replay before using it")
    if args.output.exists():
        raise FileExistsError(args.output)
    native = json.loads(args.native_report.read_text(encoding="utf-8"))
    if not native["complete"]:
        raise ValueError("Native comparison must be complete")
    cases = [
        case
        for case in native["results"]
        if case["variant"] in {"candidateLibrary", "installedLibrary"}
    ]
    if not 1 <= len(cases) <= 100:
        raise ValueError("Invalid comparison case count")
    work = args.work_directory.resolve()
    work.mkdir(parents=True, exist_ok=True)
    report = dict(
        format=1,
        complete=False,
        sqlite=sqlite3.sqlite_version,
        script_sha256=sha(Path(__file__)),
        native_report_sha256=sha(args.native_report),
        native_sources=dict(library_store=LIBRARY_STORE_SHA256, query=QUERY_SHA256),
        scope="Host replay of the pinned Android retrieval policy, including source-name routing, on the exact native-evaluated package. Host elapsed times are diagnostics, never phone latency or full-size Android import proof.",
        cases=native["cases"],
        baseline_parity=[],
        results=[],
    )

    def save():
        args.output.parent.mkdir(parents=True, exist_ok=True)
        temporary = args.output.with_suffix(".json.tmp")
        temporary.write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        os.replace(temporary, args.output)

    save()
    try:
        if args.baseline:
            baseline = unpack(args.baseline, work / "starter.sqlite")
            baseline_cases = [
                case for case in native["results"] if case["variant"] == "baselineLibrary"
            ]
            if len(baseline_cases) != len(cases):
                raise ValueError("Native report must contain matching baseline and candidate cases")
            for case in baseline_cases:
                verify_report_library(case, baseline)
                query, titles = retrieval_spec(work / "starter.sqlite", case["resolved_query"])
                route_ids = direct_title_ids(
                    work / "starter.sqlite", case["resolved_query"], titles
                )
                priority_ids = direct_title_ids(
                    work / "starter.sqlite", case["resolved_query"], titles, broad_only=True
                )
                evidence, _ = retrieve(
                    work / "starter.sqlite",
                    baseline,
                    query,
                    question=case["resolved_query"],
                    priority_ids=route_ids,
                    broad_priority_ids=priority_ids,
                )
                same = evidence == case["evidence"]
                report["baseline_parity"].append(dict(case_id=case["case_id"], identical=same))
                if not same:
                    raise ValueError(f"Host/native evidence differs: {case['case_id']}")
            save()
            print(f"Native starter parity passed for {len(baseline_cases)} cases.", flush=True)
        candidate = unpack(args.candidate, work / "expanded.sqlite")
        for case in cases:
            verify_report_library(case, candidate)
        report["candidate"] = candidate
        save()
        for case in cases:
            started = time.monotonic()
            query, titles = retrieval_spec(work / "expanded.sqlite", case["resolved_query"])
            route_ids = direct_title_ids(work / "expanded.sqlite", case["resolved_query"], titles)
            priority_ids = direct_title_ids(
                work / "expanded.sqlite", case["resolved_query"], titles, broad_only=True
            )
            evidence, candidates = retrieve(
                work / "expanded.sqlite",
                candidate,
                query,
                question=case["resolved_query"],
                priority_ids=route_ids,
                broad_priority_ids=priority_ids,
            )
            same = evidence == case["evidence"]
            report["results"].append(
                dict(
                    case_id=case["case_id"],
                    resolved_query=case["resolved_query"],
                    fts_query=query,
                    matched_titles=titles,
                    elapsed_host_ms=(time.monotonic() - started) * 1000,
                    native_evidence_identical=same,
                    evidence=evidence,
                    candidates=candidates,
                )
            )
            save()
            print(
                f"{case['case_id']}: " + ", ".join(dict.fromkeys(row["title"] for row in evidence)),
                flush=True,
            )
        report["complete"] = True
    except Exception as error:
        report["error"] = str(error)
        raise
    finally:
        save()
    print(f"Full-corpus host diagnostics saved to {args.output}", flush=True)


if __name__ == "__main__":
    main()
