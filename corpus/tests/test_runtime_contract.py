import re
from pathlib import Path
import sqlite3
import tempfile
import unittest

from blobs_corpus.runtime_contract import (
    DOCUMENT_LIMITS,
    RuntimeContractError,
    assert_installable,
    audit_library,
)

VALIDATOR_SOURCE = (
    Path(__file__).resolve().parents[2]
    / "android/app/src/main/kotlin/dev/blobs/app/data/library/LibraryValidator.kt"
)
RECORDS_SOURCE = Path(__file__).resolve().parents[1] / "blobs_corpus" / "records.py"


def library(folder: Path, body: str) -> Path:
    database = folder / "library.sqlite"
    connection = sqlite3.connect(database)
    connection.executescript(
        """
        CREATE TABLE documents (id, title, publisher, url, license, updated, saved, collection, body);
        CREATE TABLE passages (id, doc_id, section, title);
        CREATE TABLE passage_spans (passage_id, start, end, section_path);
        """
    )
    connection.execute(
        "INSERT INTO documents VALUES (?,?,?,?,?,?,?,?,?)",
        (
            "wiki-one",
            "One",
            "Pub",
            "https://example.test/one",
            "CC BY-SA 4.0",
            "2026-10-01",
            "2026-10-01",
            "Collection",
            body,
        ),
    )
    connection.execute("INSERT INTO passages VALUES (1, 'wiki-one', 'History', 'One')")
    connection.execute("INSERT INTO passage_spans VALUES (1, 0, 7, 'History')")
    connection.commit()
    connection.close()
    return database


class RuntimeContractTest(unittest.TestCase):
    def test_accepts_a_database_the_runtime_can_install(self):
        with tempfile.TemporaryDirectory() as folder:
            findings = assert_installable(library(Path(folder), "History of a place."))
            self.assertEqual(1, findings["documents"])
            self.assertEqual(0, findings["rejected_documents"])

    def test_names_a_document_whose_body_carries_a_replacement_character(self):
        with tempfile.TemporaryDirectory() as folder:
            database = library(Path(folder), "Before \ufffd after")
            self.assertEqual(1, audit_library(database)["rejected_documents"])
            with self.assertRaises(RuntimeContractError) as caught:
                assert_installable(database)
            self.assertIn("wiki-one", str(caught.exception))

    def test_names_a_document_whose_body_carries_a_null_character(self):
        with tempfile.TemporaryDirectory() as folder:
            database = library(Path(folder), "Before \x00 after")
            with self.assertRaises(RuntimeContractError):
                assert_installable(database)

    def test_refuses_a_passage_span_outside_the_document_body(self):
        with tempfile.TemporaryDirectory() as folder:
            database = library(Path(folder), "Short")
            connection = sqlite3.connect(database)
            connection.execute("UPDATE passage_spans SET end = 900")
            connection.commit()
            connection.close()
            self.assertEqual(1, audit_library(database)["rejected_passages"])
            with self.assertRaises(RuntimeContractError):
                assert_installable(database)

    def test_refuses_a_document_id_repeated_across_records(self):
        with tempfile.TemporaryDirectory() as folder:
            database = library(Path(folder), "Body")
            connection = sqlite3.connect(database)
            connection.execute(
                "INSERT INTO documents VALUES (?,?,?,?,?,?,?,?,?)",
                (
                    "wiki-one",
                    "One",
                    "Pub",
                    "https://example.test/two",
                    "CC BY-SA 4.0",
                    "2026-10-01",
                    "2026-10-01",
                    "Collection",
                    "Other body",
                ),
            )
            connection.commit()
            connection.close()
            with self.assertRaises(RuntimeContractError) as caught:
                assert_installable(database)
            self.assertIn("document ids repeat", str(caught.exception))


class RuntimeValidatorParityTest(unittest.TestCase):
    """The packaging gate is only correct while it matches the Android validator it mirrors."""

    def kotlin_limits(self) -> dict:
        source = VALIDATOR_SOURCE.read_text(encoding="utf-8")
        block = re.search(r"val limits = mapOf\((.*?)\n\s*\)", source, re.DOTALL)
        self.assertIsNotNone(block, "could not find the limits map in LibraryValidator.kt")
        return {
            name: int(value.replace("_", ""))
            for name, value in re.findall(r'"(\w+)" to ([0-9_]+)', block.group(1))
        }

    def test_document_limits_match_the_android_validator(self):
        self.assertEqual(self.kotlin_limits(), DOCUMENT_LIMITS)

    def test_no_column_is_exempt_from_the_replacement_character_rule(self):
        records = RECORDS_SOURCE.read_text(encoding="utf-8")
        self.assertIn('if "\\ufffd" in value:', records)
        self.assertNotIn('field != "body" and "\\ufffd" in value', records)

    def test_span_and_path_bounds_match_the_android_validator(self):
        source = VALIDATOR_SOURCE.read_text(encoding="utf-8")
        gate = (
            Path(__file__).resolve().parents[1] / "blobs_corpus" / "runtime_contract.py"
        ).read_text(encoding="utf-8")
        for rule in ("NOT BETWEEN 1 AND 1400", "NOT BETWEEN 1 AND 4000", "length(p.section)>300"):
            self.assertIn(rule, source, f"the Android validator no longer declares {rule}")
            self.assertIn(rule, gate, f"the packaging gate no longer declares {rule}")


if __name__ == "__main__":
    unittest.main()
