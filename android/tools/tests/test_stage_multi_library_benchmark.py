import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from stage_multi_library_benchmark import read_inputs, stage


def fixture_inputs(
    url="https://huggingface.co/datasets/nonomnonom/blobs-catalog/resolve/"
    + "a" * 40
    + "/packages/guide.blobs",
):
    payload = b"pinned package"
    digest = hashlib.sha256(payload).hexdigest()
    catalog = {
        "format": 1,
        "packages": [
            {
                "file_name": "guide.blobs",
                "url": url,
                "download_format": "blobs",
                "download_bytes": len(payload),
                "download_sha256": digest,
                "manifest": {
                    "id": "guide",
                    "version": "1",
                    "kind": "library",
                    "sha256": "b" * 64,
                },
            }
        ],
    }
    case = {
        "format": 1,
        "libraries": [
            {
                "file": "guide.blobs",
                "id": "guide",
                "version": "1",
                "payload_sha256": "b" * 64,
            }
        ],
    }
    return case, catalog, payload


class StageMultiLibraryBenchmarkTest(unittest.TestCase):
    def test_latest_multi_library_case_set_resolves_against_the_bundled_catalog(self):
        root = Path(__file__).resolve().parents[3]
        case_sets = list(
            (root / "corpus/evaluation/cases").glob("multi-library-retrieval-scaling-v*.json")
        )
        self.assertTrue(case_sets)
        latest_case_set = max(case_sets, key=lambda path: int(path.stem.rsplit("-v", 1)[1]))
        packages = read_inputs(
            latest_case_set,
            root / "android/app/src/main/assets/package-catalog.json",
        )
        self.assertTrue(packages)

    def test_resolves_only_matching_hash_pinned_library_packages(self):
        case, catalog, _ = fixture_inputs()
        with tempfile.TemporaryDirectory() as directory:
            case_path = Path(directory) / "cases.json"
            catalog_path = Path(directory) / "catalog.json"
            case_path.write_text(json.dumps(case), encoding="utf-8")
            catalog_path.write_text(json.dumps(catalog), encoding="utf-8")

            packages = read_inputs(case_path, catalog_path)

        self.assertEqual(
            [(row["id"], row["filename"]) for row in packages], [("guide", "guide.blobs")]
        )

    def test_rejects_unpinned_or_non_catalog_urls(self):
        case, catalog, _ = fixture_inputs("https://example.com/guide.blobs")
        with tempfile.TemporaryDirectory() as directory:
            case_path = Path(directory) / "cases.json"
            catalog_path = Path(directory) / "catalog.json"
            case_path.write_text(json.dumps(case), encoding="utf-8")
            catalog_path.write_text(json.dumps(catalog), encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "not a pinned public HF dataset path"):
                read_inputs(case_path, catalog_path)

    def test_reuses_only_an_existing_package_with_the_pinned_digest(self):
        case, catalog, payload = fixture_inputs()
        with tempfile.TemporaryDirectory() as directory:
            case_path = Path(directory) / "cases.json"
            catalog_path = Path(directory) / "catalog.json"
            output = Path(directory) / "packages"
            output.mkdir()
            (output / "guide.blobs").write_bytes(payload)
            case_path.write_text(json.dumps(case), encoding="utf-8")
            catalog_path.write_text(json.dumps(catalog), encoding="utf-8")

            staged = stage(read_inputs(case_path, catalog_path), output)

        self.assertEqual([path.name for path in staged], ["guide.blobs"])


if __name__ == "__main__":
    unittest.main()
