import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile
from copy import deepcopy
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from publish_hf_assets import (
    main,
    pin_uploaded_assets,
    require_release_admissions,
    validate_source_and_records,
    verify_staged_package,
)
from sign_catalog import validate_catalog


class PublishHfAssetsTest(unittest.TestCase):
    def test_signed_payload_and_bundled_catalog_keep_git_line_endings(self):
        source = Path(__file__).resolve().parents[2] / "app/src/main/assets/package-catalog.json"
        catalog = json.loads(source.read_text(encoding="utf-8"))
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            bundled = root / "app/src/main/assets/package-catalog.json"
            bundled.parent.mkdir(parents=True)
            bundled.write_text(json.dumps(catalog), encoding="utf-8")
            assets = root / "upload"
            (assets / "packages").mkdir(parents=True)
            (assets / "README.md").write_text("Public package list", encoding="utf-8")
            key = root / "test-key.pem"
            key.write_text("signing fixture", encoding="utf-8")
            signed_payloads = []

            def sign(candidate, envelope, private_key):
                signed_payloads.append(candidate.read_bytes())
                envelope.write_text("signed fixture", encoding="utf-8")

            with (
                patch("publish_hf_assets.ROOT", root),
                patch("publish_hf_assets.sign_catalog", side_effect=sign),
                patch("publish_hf_assets.run_hf", return_value=json.dumps({"sha": "a" * 40})),
                patch("publish_hf_assets.require_release_admissions"),
                patch.object(
                    sys,
                    "argv",
                    [
                        "publish_hf_assets.py",
                        "--assets",
                        str(assets),
                        "--private-key",
                        str(key),
                        "--revision",
                        str(catalog["revision"] + 1),
                    ],
                ),
            ):
                main()

            self.assertEqual([bundled.read_bytes()], signed_payloads)
            self.assertNotIn(b"\r", bundled.read_bytes())
            self.assertTrue(bundled.read_bytes().endswith(b"\n"))

    def test_release_admission_rejects_legacy_catalog_libraries(self):
        root = Path(__file__).resolve().parents[2]
        catalog = json.loads(
            (root / "app/src/main/assets/package-catalog.json").read_text(encoding="utf-8")
        )

        with self.assertRaisesRegex(ValueError, "not release-ready"):
            require_release_admissions(catalog, {})

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
                    "url": "https://huggingface.co/google/model/resolve/"
                    + "b" * 40
                    + "/frontier.gguf",
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
            "https://huggingface.co/datasets/nonomnonom/blobs-catalog/resolve/"
            + f"{revision}/packages/starter.blobs",
        )
        self.assertIn("google/model/resolve/", published["packages"][1]["url"])

    def test_rejects_manifest_drift_even_when_archive_digest_matches(self):
        payload = b"test database payload"
        manifest = dict(
            format=1,
            id="test-library",
            version="1",
            kind="library",
            title="Travel \u00b7 English",
            bytes=len(payload),
            sha256=hashlib.sha256(payload).hexdigest(),
            records_sha256="a" * 64,
            license="Test",
            origin="local:test",
        )
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "test.blobs"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("manifest.json", json.dumps(manifest, ensure_ascii=False))
                archive.writestr("library.sqlite", payload)
            item = dict(
                download_format="blobs",
                manifest=manifest,
                download_bytes=path.stat().st_size,
                download_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
            )
            verify_staged_package(path, item)
            item["manifest"] = dict(manifest, title="Changed title")
            with self.assertRaisesRegex(ValueError, "manifest differs"):
                verify_staged_package(path, item)

    def test_lane_source_lock_rebuilds_every_merged_archive_identity(self):
        def archive_lock(index):
            return {
                "format": 1,
                "id": f"stackexchange-site{index}-en-all",
                "edition": "2026-10",
                "filename": f"site{index}.stackexchange.com_en_all_2026-10.zim",
                "bytes": 1000 + index,
                "sha256": f"{index:064x}",
            }

        def canonical(path):
            return hashlib.sha256(
                json.dumps(json.loads(path.read_text(encoding="utf-8")), sort_keys=True).encode()
            ).hexdigest()

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            member_locks = []
            for index in (0, 1):
                path = root / f"stackexchange-site{index}-en-all-2026-10.json"
                path.write_text(json.dumps(archive_lock(index)), encoding="utf-8")
                member_locks.append(path)
            records = {
                "format": 1,
                "complete": True,
                "curation": {},
                "source": {"id": "howto-repair", "edition": "2026-10.3"},
                "identity": {},
                "archives": [
                    dict(archive_lock(index), lock_sha256=f"{index + 9:064x}") for index in (0, 1)
                ],
            }
            records_path = root / "records.json"
            records_path.write_text(json.dumps(records), encoding="utf-8")

            def lane_lock():
                return {
                    "format": 1,
                    "id": "howto-repair",
                    "edition": "2026-10.3",
                    "archives": [
                        dict(
                            archive_lock(index),
                            lock=member_locks[index].name,
                            lock_sha256=canonical(member_locks[index]),
                            manifest_sha256=f"{index + 9:064x}",
                        )
                        for index in (0, 1)
                    ],
                }

            lane_path = root / "howto-repair-en-2026-10.3.json"
            lane_path.write_text(json.dumps(lane_lock()), encoding="utf-8")
            validate_source_and_records(lane_path, records_path)

            tampered = lane_lock()
            tampered["archives"][0]["sha256"] = "f" * 64
            lane_path.write_text(json.dumps(tampered), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "differs from records"):
                validate_source_and_records(lane_path, records_path)

            lane_path.write_text(json.dumps(lane_lock()), encoding="utf-8")
            member_locks[1].write_text(
                json.dumps(dict(archive_lock(1), note="rewritten")), encoding="utf-8"
            )
            with self.assertRaisesRegex(ValueError, "does not identify"):
                validate_source_and_records(lane_path, records_path)

            member_locks[1].write_text(json.dumps(archive_lock(1)), encoding="utf-8")
            records["archives"].append(dict(archive_lock(2), lock_sha256="e" * 64))
            records_path.write_text(json.dumps(records), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "disagree on archives"):
                validate_source_and_records(lane_path, records_path)

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
