import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from blobs_corpus.extract import write_lines
from blobs_corpus.records import documents
from blobs_corpus.subset import subset


class ArticleSubsetTests(unittest.TestCase):
    def test_selection_preserves_text_rights_and_lineage_and_is_repeatable(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source, lock_path, lock, original = self.fixture(root)
            output = subset(lock_path, source, root / "selected.jsonl.gz")
            repeated = subset(lock_path, source, root / "repeated.jsonl.gz")
            selected = list(documents(output))
            self.assertEqual([item["id"] for item in selected], ["bali", "ubud"])
            for item in selected:
                expected = next(row for row in original if row["id"] == item["id"])
                for field in ("body", "title", "url", "license", "updated", "saved"):
                    self.assertEqual(item[field], expected[field])
                self.assertEqual(item["provenance"]["source"], "original snapshot")
                self.assertEqual(
                    item["provenance"]["selection"]["input_sha256"], lock["input_sha256"]
                )
            self.assertEqual(output.read_bytes(), repeated.read_bytes())
            with self.assertRaises(FileExistsError):
                subset(lock_path, source, output)

    def test_changed_missing_or_repeated_articles_never_publish_output(self):
        for failure in (
            "digest",
            "body",
            "title",
            "url",
            "missing",
            "duplicate-lock",
            "duplicate-input",
        ):
            with self.subTest(failure=failure), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                source, lock_path, lock, original = self.fixture(root)
                if failure == "digest":
                    lock["input_sha256"] = "0" * 64
                elif failure == "body":
                    lock["documents"][0]["body_sha256"] = "0" * 64
                elif failure == "title":
                    lock["documents"][0]["title"] = "Changed title"
                elif failure == "url":
                    lock["documents"][0]["url"] += "/changed"
                elif failure == "missing":
                    lock["documents"][0]["id"] = "missing"
                elif failure == "duplicate-lock":
                    lock["documents"].append(copy.deepcopy(lock["documents"][0]))
                else:
                    write_lines(source, original + [original[0]])
                    lock["input_sha256"] = hashlib.sha256(source.read_bytes()).hexdigest()
                lock_path.write_text(json.dumps(lock), encoding="utf-8")
                output = root / "selected.jsonl.gz"
                with self.assertRaises(ValueError):
                    subset(lock_path, source, output)
                self.assertFalse(output.exists())

    def fixture(self, root):
        rows = [
            dict(
                id=name,
                title=name.title(),
                publisher="Wikivoyage contributors",
                url=f"https://en.wikivoyage.org/wiki/{name.title()}",
                license="CC BY-SA 4.0; author history",
                updated="Snapshot 2026-09",
                saved="2026-10-20",
                body=f"A reviewed guide to {name}.",
                collection="Full travel collection",
                provenance={"source": "original snapshot"},
            )
            for name in ("bali", "tokyo", "ubud")
        ]
        source = root / "source.jsonl.gz"
        write_lines(source, rows)
        lock = dict(
            format=1,
            id="bali-travel",
            collection="Bali travel",
            input_sha256=hashlib.sha256(source.read_bytes()).hexdigest(),
            documents=[
                dict(
                    id=row["id"],
                    title=row["title"],
                    url=row["url"],
                    body_sha256=hashlib.sha256(row["body"].encode("utf-8")).hexdigest(),
                )
                for row in (rows[0], rows[2])
            ],
        )
        path = root / "selection.json"
        path.write_text(json.dumps(lock), encoding="utf-8")
        return source, path, lock, rows
