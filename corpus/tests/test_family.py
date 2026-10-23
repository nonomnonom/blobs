import gzip
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from blobs_corpus.extract import verified_shards, write_json
from blobs_corpus.family import merge
from blobs_corpus.portfolio import install_identity


def write_member(root: Path, slug: str, language: str, records, identity=None):
    edition = root / slug
    edition.mkdir(parents=True)
    name = f"{slug}.documents.jsonl.gz"
    with gzip.open(edition / name, "wt", encoding="utf-8") as stream:
        for url, body in records:
            document = {
                "id": "doc-" + hashlib.sha256(url.encode()).hexdigest()[:16],
                "url": url,
                "title": url.rsplit("/", 1)[-1],
                "body": body,
                "publisher": f"{slug} contributors",
                "license": "CC BY-SA 4.0; Stack Exchange contributors",
                "provenance": {"body_sha256": hashlib.sha256(body.encode()).hexdigest()},
            }
            stream.write(json.dumps(document, ensure_ascii=False) + "\n")
    raw = (edition / name).read_bytes()
    info = {"name": name, "bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}
    manifest = {
        "format": 1,
        "complete": True,
        "next_entry": len(records),
        "total_entries": len(records),
        "identity": identity
        or {
            "extractor": "4",
            "extractor_sha256": "a" * 64,
            "python": "3.13.12",
            "dependencies": {"libzim": "3.13.1", "selectolax": "1.0.0"},
        },
        "source": {
            "id": f"stackexchange-{slug}-en-all",
            "edition": "2026-10",
            "filename": f"{slug}.zim",
            "url": f"https://download.kiwix.org/zim/stack_exchange/{slug}.zim",
            "bytes": 1234,
            "sha256": hashlib.sha256(slug.encode()).hexdigest(),
            "publisher": f"{slug} network contributors",
            "license": "CC BY-SA 4.0; Stack Exchange contributors",
            "collection": f"Stack Exchange · {slug}",
        },
        "archive_metadata": {"Language": language},
        "totals": {"documents": len(records), "text_characters": sum(len(b) for _, b in records)},
        "shards": [
            dict(start=0, end=len(records), counts={"documents": len(records)}, files={"documents": info})
        ],
    }
    write_json(edition / "manifest.json", manifest)
    return edition / "manifest.json"


class FamilyTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)

    def tearDown(self):
        self.temporary.cleanup()

    def merge_two(self, **kwargs):
        first = write_member(self.root, "cooking", "eng", [("https://cooking.stackexchange.com/q/1", "one body text")])
        second = write_member(self.root, "money", "eng", [("https://money.stackexchange.com/q/2", "another body text")])
        return merge([first, second], self.root / "lane", **kwargs)

    def test_lane_manifest_offsets_lineage_and_totals(self):
        lane = self.merge_two(
            lane="howto-practical",
            edition="2026-10.1",
            collection="Practical how-to",
            scope="Everyday practical questions.",
        )
        verified_shards(self.root / "lane/manifest.json")
        self.assertEqual(lane["lane"], "howto-practical")
        self.assertEqual(lane["next_entry"], 2)
        self.assertEqual(lane["totals"]["documents"], 2)
        self.assertEqual([s["start"] for s in lane["shards"]], [0, 1])
        self.assertEqual([s["end"] for s in lane["shards"]], [1, 2])
        self.assertEqual({s["archive"] for s in lane["shards"]}, {
            "stackexchange-cooking-en-all",
            "stackexchange-money-en-all",
        })
        self.assertEqual({a["id"] for a in lane["archives"]}, {
            "stackexchange-cooking-en-all",
            "stackexchange-money-en-all",
        })
        self.assertTrue(all(len(a["lock_sha256"]) == 64 for a in lane["archives"]))
        self.assertIn("CC BY-SA 4.0", lane["source"]["license"])
        self.assertEqual(len(lane["source"]["origins"]), 2)

    def test_refuses_the_same_source_work_twice(self):
        first = write_member(self.root, "cooking", "eng", [("https://cooking.stackexchange.com/q/1", "one body text")])
        dupe = write_member(self.root, "money", "eng", [("https://cooking.stackexchange.com/q/1", "different text")])
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            merge(
                [first, dupe],
                self.root / "lane",
                lane="howto-practical",
                edition="2026-10.1",
                collection="Practical how-to",
                scope="Everyday practical questions.",
            )

    def test_refuses_a_different_extractor_identity(self):
        first = write_member(self.root, "cooking", "eng", [("https://cooking.stackexchange.com/q/1", "one body text")])
        other = write_member(
            self.root,
            "money",
            "eng",
            [("https://money.stackexchange.com/q/2", "another body text")],
            identity={
                "extractor": "4",
                "extractor_sha256": "b" * 64,
                "python": "3.13.12",
                "dependencies": {"libzim": "3.13.1", "selectolax": "1.0.0"},
            },
        )
        with self.assertRaisesRegex(ValueError, "different builder"):
            merge(
                [first, other],
                self.root / "lane",
                lane="howto-practical",
                edition="2026-10.1",
                collection="Practical how-to",
                scope="Everyday practical questions.",
            )

    def test_refuses_a_non_english_member(self):
        first = write_member(self.root, "cooking", "eng", [("https://cooking.stackexchange.com/q/1", "one body text")])
        spanish = write_member(self.root, "money", "spa", [("https://money.stackexchange.com/q/2", "otro texto")])
        with self.assertRaisesRegex(ValueError, "Supply language policy"):
            merge(
                [first, spanish],
                self.root / "lane",
                lane="howto-practical",
                edition="2026-10.1",
                collection="Practical how-to",
                scope="Everyday practical questions.",
            )

    def test_lanes_hold_their_own_install_slots(self):
        practical = install_identity({"source": {"id": "howto-practical"}, "lane": "howto-practical", "archive_metadata": {"Language": "eng"}})
        repair = install_identity({"source": {"id": "howto-repair"}, "lane": "howto-repair", "archive_metadata": {"Language": "en"}})
        successor = install_identity({"source": {"id": "howto-practical"}, "lane": "howto-practical", "archive_metadata": {"Language": "eng"}})
        single = install_identity({"source": {"id": "wikivoyage-en-all-nopic"}, "archive_metadata": {"Language": "eng"}})
        self.assertNotEqual(practical, repair)
        self.assertEqual(practical, successor)
        self.assertEqual(single, ("wikivoyage", "eng"))


if __name__ == "__main__":
    unittest.main()
