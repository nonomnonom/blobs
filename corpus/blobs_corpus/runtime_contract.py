"""Refuse a library database the Android runtime validator would reject.

`LibraryValidator` in the app decides what an installed library may contain, and a violation makes
the whole archive uninstallable on device - after the supply registry already admitted it. These
invariants mirror that validator so the defect surfaces during the build instead of during install.
Keep the two in step when either side changes.
"""

from __future__ import annotations

from pathlib import Path
import sqlite3

DOCUMENT_LIMITS = {
    "id": 100,
    "title": 300,
    "publisher": 300,
    "url": 2000,
    "license": 4000,
    "updated": 80,
    "saved": 80,
    "collection": 200,
    "body": 1_000_000,
}
MAXIMUM_REPORTED_IDENTITIES = 8
MAXIMUM_DOCUMENTS = 5_000_000


class RuntimeContractError(ValueError):
    """The database cannot be installed by the Android runtime."""


def _document_text_rule() -> str:
    return " OR ".join(
        f"typeof({column})!='text' OR length({column})>{maximum} "
        f"OR instr({column},char(0))>0 OR instr({column},char(65533))>0"
        for column, maximum in DOCUMENT_LIMITS.items()
    )


def audit_library(database: Path) -> dict[str, object]:
    """Count the records a runtime install would reject, without raising."""
    connection = sqlite3.connect(f"file:{Path(database).as_posix()}?mode=ro", uri=True)
    try:
        cursor = connection.cursor()
        documents = cursor.execute("SELECT count(*) FROM documents").fetchone()[0]
        cursor.execute(
            f"SELECT id FROM documents WHERE {_document_text_rule()} "
            f"LIMIT {MAXIMUM_REPORTED_IDENTITIES}"
        )
        rejected_documents = [str(row[0]) for row in cursor.fetchall()]
        duplicate_document_ids = cursor.execute(
            "SELECT count(*) FROM (SELECT id FROM documents GROUP BY id HAVING count(*)>1)"
        ).fetchone()[0]
        rejected_passages = cursor.execute(
            """
            SELECT count(*) FROM passages p
            LEFT JOIN documents d ON d.id=p.doc_id
            LEFT JOIN passage_spans s ON s.passage_id=p.id
            WHERE d.id IS NULL OR s.passage_id IS NULL
               OR (p.title!='' AND p.title!=d.title)
               OR typeof(p.section)!='text' OR length(p.section)>300
               OR instr(p.section,char(0))>0 OR instr(p.section,char(65533))>0
               OR s.start<0 OR s.end<=s.start OR s.end>length(d.body)
               OR s.end-s.start NOT BETWEEN 1 AND 1400
               OR length(s.section_path) NOT BETWEEN 1 AND 4000
               OR instr(s.section_path,char(0))>0
               OR instr(s.section_path,char(65533))>0
            """
        ).fetchone()[0]
        return {
            "documents": documents,
            "duplicate_document_ids": duplicate_document_ids,
            "rejected_documents": len(rejected_documents),
            "rejected_document_examples": rejected_documents,
            "rejected_passages": rejected_passages,
        }
    finally:
        connection.close()


def assert_installable(database: Path) -> dict[str, object]:
    """Return the audit for a database the app can install, or explain why it cannot."""
    findings = audit_library(database)
    reasons: list[str] = []
    if not 0 < findings["documents"] <= MAXIMUM_DOCUMENTS:
        reasons.append(f"document count {findings['documents']} is outside the installed limit")
    if findings["duplicate_document_ids"]:
        reasons.append(f"{findings['duplicate_document_ids']} document ids repeat")
    if findings["rejected_documents"]:
        examples = ", ".join(findings["rejected_document_examples"])
        reasons.append(
            f"{findings['rejected_documents']} documents break the runtime text contract "
            f"(NUL, U+FFFD, or a length limit): {examples}"
        )
    if findings["rejected_passages"]:
        reasons.append(f"{findings['rejected_passages']} passages break the runtime span contract")
    if reasons:
        raise RuntimeContractError(f"{Path(database).name} is uninstallable: " + "; ".join(reasons))
    return findings
