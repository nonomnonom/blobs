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
                    {
                        "url": url,
                        "body": body,
                        "provenance": {"body_sha256": hashlib.sha256(body.encode()).hexdigest()},
                    },
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

    def test_install_identity_separates_projects(self):
        english = write_edition(
            self.root, "a", "wikipedia-en-top1m-mini", "eng", [("https://en.wikipedia.org/wiki/A", "one")]
        )
        self.assertEqual(install_identity(json.loads(english.read_text(encoding="utf-8"))), ("wikipedia", "eng"))

    def test_refuses_a_non_english_edition(self):
        portuguese = write_edition(
            self.root, "travel-pt", "wikivoyage-pt-all-nopic", "por", [("https://pt.wikivoyage.org/wiki/A", "um")]
        )
        with self.assertRaisesRegex(ValueError, "Supply language policy"):
            assert_distinct(portuguese, self.records, self.cache)

    def test_rejects_an_edition_that_repeats_the_same_works(self):
        write_edition(
            self.root, "travel", "wikivoyage-en-all-nopic", "eng", [("https://en.wikipedia.org/wiki/A", "one")]
        )
        repeat = write_edition(
            self.root, "reference", "wikipedia-en-all-nopic", "eng", [("https://en.wikipedia.org/wiki/A", "one")]
        )
        with self.assertRaisesRegex(ValueError, "Duplicate supply"):
            assert_distinct(repeat, self.records, self.cache)

    def test_rejects_a_second_library_occupying_the_same_install_slot(self):
        write_edition(
            self.root, "simple", "wikipedia-en-simple-all", "eng", [("https://simple.wikipedia.org/wiki/A", "simpler")]
        )
        successor = write_edition(
            self.root, "full", "wikipedia-en-all-nopic", "eng", [("https://en.wikipedia.org/wiki/A", "one")]
        )
        with self.assertRaisesRegex(ValueError, "Install-identity collision"):
            assert_distinct(successor, self.records, self.cache)

    def test_allows_a_different_subject_category(self):
        write_edition(
            self.root, "travel", "wikivoyage-en-all-nopic", "eng", [("https://en.wikivoyage.org/wiki/A", "one")]
        )
        dictionary = write_edition(
            self.root, "dictionary", "wiktionary-en-all-nopic", "eng", [("https://en.wiktionary.org/wiki/A", "meaning")]
        )
        result = assert_distinct(dictionary, self.records, self.cache)
        self.assertEqual(result["identity"], ["wiktionary", "eng"])
        self.assertEqual(result["repeated_supply"], [])
        self.assertEqual(result["identity_slots_taken"], [])

    def test_caches_fingerprints_by_manifest_digest(self):
        edition = write_edition(
            self.root, "one", "wiktionary-en-all-nopic", "eng", [("https://en.wiktionary.org/wiki/A", "meaning")]
        )
        findings(edition, self.records, self.cache)
        self.assertEqual(len(list(self.cache.glob("*.json.gz"))), 1)
        findings(edition, self.records, self.cache)
        self.assertEqual(len(list(self.cache.glob("*.json.gz"))), 1)


if __name__ == "__main__":
    unittest.main()
