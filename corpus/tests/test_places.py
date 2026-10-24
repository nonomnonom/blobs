import gzip
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from blobs_corpus.extract import verified_shards
from blobs_corpus.places import extract, place_indicator, read_selection


def _digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


class SelectionTest(unittest.TestCase):
    def test_amenity_only_selection_names_the_matching_tag(self):
        selection = read_selection({"amenities": ["pharmacy", "hospital"]})
        self.assertEqual(selection["amenities"], ["hospital", "pharmacy"])
        self.assertEqual(
            place_indicator({"amenity": "pharmacy", "name": "Wellcare"}, selection),
            "amenity=pharmacy",
        )
        self.assertIsNone(place_indicator({"amenity": "restaurant", "name": "Warung"}, selection))

    def test_tag_rule_is_required_when_declared(self):
        selection = read_selection(
            {
                "amenities": ["restaurant", "cafe"],
                "tag_any": [{"key": "diet:vegan", "values": ["yes", "only"]}],
            }
        )
        self.assertEqual(
            place_indicator({"amenity": "cafe", "diet:vegan": "only"}, selection),
            "diet:vegan includes only",
        )
        self.assertIsNone(place_indicator({"amenity": "cafe", "diet:vegetarian": "yes"}, selection))
        self.assertEqual(
            place_indicator(
                {"amenity": "restaurant", "cuisine": "italian;vegan"},
                read_selection(
                    {
                        "amenities": ["restaurant"],
                        "tag_any": [{"key": "cuisine", "values": ["vegan"]}],
                    }
                ),
            ),
            "cuisine includes vegan",
        )

    def test_selection_rejects_unreviewable_rules(self):
        for value in (
            None,
            {"amenities": []},
            {"amenities": ["Pharmacy"]},
            {
                "amenities": [
                    "pharmacy",
                    "pharmacy",
                    "cafe",
                    "hospital",
                    "clinic",
                    "dentist",
                    "bank",
                    "atm",
                    "fuel",
                    "water_point",
                    "shelter",
                    "toilets",
                    "kindergarten",
                    "school",
                ]
            },
            {"amenities": ["pharmacy"], "tag_any": [{"key": "diet:vegan"}]},
            {"amenities": ["pharmacy"], "tag_any": [{"key": "diet:vegan", "values": []}]},
            {"amenities": ["pharmacy"], "tag_any": [{"key": "bad key", "values": ["yes"]}]},
        ):
            with self.assertRaises(ValueError):
                read_selection(value)

    def test_tags_are_normalized_before_matching(self):
        selection = read_selection({"amenities": ["pharmacy"]})
        self.assertIsNone(place_indicator({}, selection))
        self.assertEqual(
            place_indicator({"amenity": " Pharmacy ", "name": "  "}, selection), "amenity=pharmacy"
        )


class ExtractionManifestTest(unittest.TestCase):
    def test_extract_writes_a_verified_manifest_that_names_the_lock(self):
        query = '[out:json][timeout:60];node["amenity"="pharmacy"](0,0,1,1);out;'
        snapshot = {
            "version": 0.6,
            "osm3s": {"timestamp_osm_base": "2026-10-24T03:15:54Z"},
            "elements": [
                {
                    "type": "node",
                    "id": 11,
                    "lat": -1.28,
                    "lon": 36.82,
                    "tags": {"amenity": "pharmacy", "name": "Wellcare"},
                },
                {
                    "type": "node",
                    "id": 12,
                    "lat": -1.28,
                    "lon": 36.82,
                    "tags": {"amenity": "pharmacy"},
                },
            ],
        }
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            snapshot_path = root / "snapshot.json"
            snapshot_path.write_text(json.dumps(snapshot), encoding="utf-8")
            lock = {
                "format": 1,
                "id": "test-places",
                "edition": "2026-10.1",
                "publisher": "OpenStreetMap contributors",
                "license": "ODbL 1.0",
                "collection": "Test places",
                "origin": "https://www.openstreetmap.org/copyright",
                "saved": "2026-10-24",
                "source": {
                    "endpoint": "https://example.invalid/api/interpreter",
                    "query": query,
                    "query_sha256": hashlib.sha256(query.encode()).hexdigest(),
                    "snapshot_bytes": snapshot_path.stat().st_size,
                    "snapshot_sha256": _digest(snapshot_path),
                    "osm_base": "2026-10-24T03:15:54Z",
                    "elements": 2,
                    "matching_elements": 1,
                    "selection": {"amenities": ["pharmacy"], "tag_any": []},
                },
                "review": {
                    "reviewer": "test reviewer",
                    "reviewed": "2026-10-24",
                    "notes": "Synthetic snapshot for the extraction contract.",
                },
            }
            lock_path = root / "lock.json"
            lock_path.write_text(json.dumps(lock), encoding="utf-8")
            output = root / "reviewed"
            self.assertEqual(extract(lock_path, snapshot_path, output), output / "manifest.json")

            manifest = verified_shards(output / "manifest.json")
            self.assertTrue(manifest["complete"])
            self.assertEqual(
                manifest["identity"]["lock_sha256"],
                hashlib.sha256(json.dumps(lock, sort_keys=True).encode()).hexdigest(),
            )
            self.assertEqual(manifest["source"]["id"], "test-places")
            self.assertEqual(manifest["totals"]["documents"], 1)
            self.assertEqual(
                [item["id"] for item in manifest["curation"]["excluded_documents"]],
                ["osm-node-12"],
            )
            with gzip.open(output / "documents.jsonl.gz", "rt", encoding="utf-8") as lines:
                records = [json.loads(line) for line in lines]
            self.assertEqual([item["title"] for item in records], ["Wellcare"])


if __name__ == "__main__":
    unittest.main()
