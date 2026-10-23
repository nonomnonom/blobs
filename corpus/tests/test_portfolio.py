import gzip
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from blobs_corpus.portfolio import assert_distinct, findings, install_identity


def write_edition(root: Path, name: str, source_id: str, language: str, records: list[tuple[str, str]]):
    edition = root / "records" / name
    edition.mkdir(parents=True)
    shard = "00000000-00000002.documents.jsonl.gz"
    with gzip.open(edition / shard, "wt", encoding="utf-8") as stream:
        for url, body in records:
            stream.write(
                json.dumps(
                    {"url": url, "body": body, "provenance": {"body_sha256": hashlib.sha256(body.encode()).hexdigest()}},
                    ensure_ascii=False,
                )
                + "\n"
            )
    manifest = {
        "complete": True,
        "curation": {"policy": "Kiwix article-only review v1"},
        "source": {"id": source_id},
        "archive_metadata": {"Language": language},
        "shards": [{"files": {"documents": {"name": shard}}}],
    }
    (edition / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
    return edition / "manifest.json"


class PortfolioTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.cache = self.root / "cache"
        self.records = self.root / "records"

    def tearDown(self):
        self.temporary.cleanup()

    def test_install_identity_separates_languages_and_projects(self):
        english = write_edition(self.root, "a", "wikipedia-en-top1m-mini", "eng", [("https://en.wikipedia.org/wiki/A", "one")])
        self.assertEqual(install_identity(json.loads(english.read_text(encoding="utf-8"))), ("wikipedia", "eng"))

    def test_rejects_a_second_edition_that_repeats_the_same_works(self):
        write_edition(self.root, "full-text", "wikipedia-en-top", "eng", [("https://en.wikipedia.org/wiki/A", "one")])
        duplicate = write_edition(
            self.root,
            "subset",
            "wikipedia-en-mini",
            "ind",
            [
                ("https://en.wikipedia.org/wiki/A", "one"),
                ("https://id.wikipedia.org/wiki/B", "dua"),
            ],
        )
        with self.assertRaisesRegex(ValueError, "Duplicate supply"):
            assert_distinct(duplicate, self.records, self.cache)

    def test_rejects_a_second_library_occupying_the_same_install_slot(self):
        write_edition(self.root, "simple", "wikipedia-en-simple-all", "eng", [("https://simple.wikipedia.org/wiki/A", "simpler")])
        successor = write_edition(self.root, "full", "wikipedia-en-all-nopic", "eng", [("https://en.wikipedia.org/wiki/A", "one")])
        with self.assertRaisesRegex(ValueError, "Install-identity collision"):
            assert_distinct(successor, self.records, self.cache)

    def test_allows_a_distinct_language_library_without_shared_works(self):
        write_edition(self.root, "english", "wikivoyage-en-all-nopic", "eng", [("https://en.wikivoyage.org/wiki/A", "one")])
        portugal = write_edition(
            self.root, "portuguese", "wikivoyage-pt-all-nopic", "por", [("https://pt.wikivoyage.org/wiki/A", "um")]
        )
        result = assert_distinct(portugal, self.records, self.cache)
        self.assertEqual(result["identity"], ["wikivoyage", "por"])
        self.assertEqual(result["repeated_supply"], [])
        self.assertEqual(result["identity_slots_taken"], [])

    def test_caches_fingerprints_by_manifest_digest(self):
        edition = write_edition(self.root, "one", "wikipedia-id-all-mini", "ind", [("https://id.wikipedia.org/wiki/A", "dua")])
        findings(edition, self.records, self.cache)
        self.assertEqual(len(list(self.cache.glob("*.json.gz"))), 1)
        findings(edition, self.records, self.cache)
        self.assertEqual(len(list(self.cache.glob("*.json.gz"))), 1)


if __name__ == "__main__":
    unittest.main()
