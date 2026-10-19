import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile
from copy import deepcopy

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from publish_hf_assets import pin_uploaded_assets, verify_staged_package
from sign_catalog import validate_catalog


class PublishHfAssetsTest(unittest.TestCase):
    def test_catalog_signer_rejects_replacement_characters_in_display_text(self):
        root = Path(__file__).resolve().parents[2]
        catalog = json.loads(
            (root / "app/src/main/assets/package-catalog.json").read_text(encoding="utf-8")
        )
        valid = deepcopy(catalog)
        validate_catalog(valid)

        valid["packages"][0]["description"] += " \ufffd"
        with self.assertRaisesRegex(SystemExit, "replacement character"):
            validate_catalog(valid)

    def test_signer_requires_positive_integer_revision(self):
        root = Path(__file__).resolve().parents[2]
        catalog = json.loads(
            (root / "app/src/main/assets/package-catalog.json").read_text(encoding="utf-8")
        )
        for revision in (None, 0, -1, True, "1", 1.5, 9_007_199_254_740_992):
            with self.subTest(revision=revision):
                candidate = deepcopy(catalog)
                candidate["revision"] = revision
                with self.assertRaisesRegex(SystemExit, "revision"):
                    validate_catalog(candidate)

    def test_pins_only_verified_local_packages_to_the_uploaded_revision(self):
        payload = b"verified package"
        revision = "a" * 40
        catalog = {
            "packages": [
                {
                    "file_name": "starter.blobs",
                    "url": "https://github.com/nonomnonom/blobs/releases/download/v0.3.2/starter.blobs",
                    "download_bytes": len(payload),
                    "download_sha256": hashlib.sha256(payload).hexdigest(),
                },
                {
                    "file_name": "frontier.gguf",
                    "url": "https://huggingface.co/google/model/resolve/" + "b" * 40 + "/frontier.gguf",
                    "download_bytes": 10,
                    "download_sha256": "c" * 64,
                },
            ]
        }
        with tempfile.TemporaryDirectory() as directory:
            package_path = Path(directory) / "starter.blobs"
            package_path.write_bytes(payload)

            published = pin_uploaded_assets(catalog, revision, Path(directory))

        self.assertEqual(
            published["packages"][0]["url"],
            "https://huggingface.co/datasets/nonomnonom/blobs-catalog/resolve/" +
            f"{revision}/packages/starter.blobs",
        )
        self.assertIn("google/model/resolve/", published["packages"][1]["url"])

    def test_rejects_manifest_drift_even_when_archive_digest_matches(self):
        payload = b"test database payload"
        manifest = dict(format=1, id="test-library", version="1", kind="library",
                        title="Travel \u00b7 English", bytes=len(payload),
                        sha256=hashlib.sha256(payload).hexdigest(), license="Test", origin="local:test")
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "test.blobs"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("manifest.json", json.dumps(manifest, ensure_ascii=False))
                archive.writestr("library.sqlite", payload)
            item = dict(download_format="blobs", manifest=manifest,
                        download_bytes=path.stat().st_size,
                        download_sha256=hashlib.sha256(path.read_bytes()).hexdigest())
            verify_staged_package(path, item)
            item["manifest"] = dict(manifest, title="Changed title")
            with self.assertRaisesRegex(ValueError, "manifest differs"):
                verify_staged_package(path, item)

    def test_rejects_packages_that_changed_after_the_catalog_was_written(self):
        with tempfile.TemporaryDirectory() as directory:
            package_path = Path(directory) / "starter.blobs"
            package_path.write_bytes(b"changed package")
            catalog = {
                "packages": [
                    {
                        "file_name": package_path.name,
                        "url": "https://github.com/nonomnonom/blobs/releases/download/v0.3.2/starter.blobs",
                        "download_bytes": 7,
                        "download_sha256": "0" * 64,
                    }
                ]
            }

            with self.assertRaisesRegex(ValueError, "Package size differs"):
                pin_uploaded_assets(catalog, "a" * 40, Path(directory))


if __name__ == "__main__":
    unittest.main()
