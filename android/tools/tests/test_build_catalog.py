import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import build_catalog
from build_catalog import (
    REGISTRY,
    build_catalog as compile_catalog,
    drift_report,
    library_description,
    library_language,
)
from publish_hf_assets import ASSET_PREFIX, read_release_admissions

REVISION = "0" * 40


def entry(package_id: str, **changes: object) -> dict:
    item = {
        "file_name": f"{package_id}.blobs",
        "url": f"{ASSET_PREFIX}/{REVISION}/packages/{package_id}.blobs",
        "download_bytes": 10,
        "manifest": {"id": package_id, "version": "1", "kind": "library"},
    }
    item.update(changes)
    return item


class LanguageTest(unittest.TestCase):
    def test_resolves_the_language_from_a_kiwix_edition_identity(self):
        self.assertEqual("English", library_language({"id": "wikivoyage-en-all-nopic"}))

    def test_prefers_a_declared_language_code(self):
        self.assertEqual("Portuguese", library_language({"id": "any-en-id", "language": "pt"}))

    def test_refuses_an_edition_identity_without_one_known_language(self):
        with self.assertRaises(SystemExit):
            library_language({"id": "unlabelled-collection"})


class DescriptionTest(unittest.TestCase):
    def test_refuses_copy_longer_than_the_catalog_limit(self):
        profile = {"scope": "s" * 1200, "limitations": "l" * 900}
        with self.assertRaises(SystemExit):
            library_description(profile, 10)


class DriftReportTest(unittest.TestCase):
    def test_names_a_library_the_supply_registry_no_longer_admits(self):
        findings = drift_report(
            {"release_tag": "v1.0.0", "packages": [entry("retired-pack")]},
            {"release_tag": "v1.0.0", "packages": []},
        )
        self.assertEqual(
            ["retired-pack@1 is advertised but no longer admitted for release"], findings
        )

    def test_names_an_admission_the_shipped_catalog_does_not_advertise(self):
        findings = drift_report(
            {"release_tag": "v1.0.0", "packages": []},
            {"release_tag": "v1.0.0", "packages": [entry("new-library")]},
        )
        self.assertEqual(
            ["new-library@1 is admitted for release but not advertised"],
            [finding for finding in findings if "admitted" in finding],
        )

    def test_reports_a_changed_digest_and_ignores_the_pinned_upload_revision(self):
        shipped = entry("library", download_sha256="a" * 64)
        rebuilt = entry("library", download_sha256="a" * 64)
        rebuilt["url"] = rebuilt["url"].replace(REVISION, "1" * 40)
        self.assertEqual(
            [],
            drift_report(
                {"release_tag": "v1.0.0", "packages": [shipped]},
                {"release_tag": "v1.0.0", "packages": [rebuilt]},
            ),
        )
        rebuilt["download_sha256"] = "b" * 64
        findings = drift_report(
            {"release_tag": "v1.0.0", "packages": [shipped]},
            {"release_tag": "v1.0.0", "packages": [rebuilt]},
        )
        self.assertEqual(
            ["library@1 field download_sha256 differs from the supply registry"], findings
        )


class ReviewedPortfolioTest(unittest.TestCase):
    """The reviewed admissions must compile into a catalog the existing publisher accepts."""

    def test_builds_every_admitted_library_from_its_receipt_and_profile(self):
        admissions = read_release_admissions(REGISTRY)
        self.assertTrue(admissions, "the supply registry admits no library")
        starter = min(package_id for package_id, _ in admissions)
        catalog = compile_catalog(REGISTRY, "v0.0.0", REVISION, starter, "0.0.0", 1)
        libraries = [item for item in catalog["packages"] if item["role"] != "model"]
        self.assertEqual(
            {f"{item['manifest']['id']}@{item['manifest']['version']}" for item in libraries},
            {f"{package_id}@{version}" for package_id, version in admissions},
        )
        self.assertEqual(1, sum(1 for item in libraries if item["role"] == "starter"))
        for item in libraries:
            manifest = item["manifest"]
            admission = admissions[(manifest["id"], manifest["version"])]
            self.assertEqual(admission["records_sha256"], manifest["records_sha256"])
            self.assertEqual(admission["archive_sha256"], item["download_sha256"])
            self.assertEqual(admission["library_profile"], item["library_profile"])

    def test_refuses_a_starter_that_data_supply_does_not_admit(self):
        with self.assertRaises(SystemExit):
            compile_catalog(REGISTRY, "v0.0.0", REVISION, "unadmitted-library", "0.0.0", 1)


class ModelPublicationTest(unittest.TestCase):
    """A model in the lock is not automatically a model the app may advertise."""

    lock = {
        "alpha": {
            "id": "alpha-model",
            "title": "Alpha",
            "revision": "a" * 40,
            "repository": "org/alpha",
            "file": "alpha.gguf",
            "bytes": 10,
            "sha256": "b" * 64,
            "license": "MIT",
        }
    }

    def packages(self, publication):
        entry = {
            "title": "Alpha",
            "description": "Local test model.",
            "language": "English",
            "publisher": "Tester",
            "minimum_ram_mb": 1,
            "recommended_up_to_memory_mb": 2,
        }
        if publication is not None:
            entry["publication"] = publication
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            lock = root / "models.lock.json"
            present = root / "models.present.json"
            lock.write_text(json.dumps(self.lock), encoding="utf-8")
            present.write_text(
                json.dumps({"format": 1, "models": {"alpha-model": entry}}), encoding="utf-8"
            )
            with (
                mock.patch.object(build_catalog, "MODELS_LOCK", lock),
                mock.patch.object(build_catalog, "MODELS_PRESENTATION", present),
            ):
                return build_catalog.model_packages("0.0.0")

    def test_authorized_models_are_catalogued(self):
        self.assertEqual(
            ["alpha-model"], [p["manifest"]["id"] for p in self.packages("authorized")]
        )

    def test_local_only_models_are_left_out(self):
        with self.assertRaises(SystemExit) as caught:
            self.packages("local-only")
        self.assertIn("authorized for publication", str(caught.exception))

    def test_an_undeclared_publication_state_stops_the_build(self):
        with self.assertRaises(SystemExit) as caught:
            self.packages(None)
        self.assertIn("publication state", str(caught.exception))


if __name__ == "__main__":
    unittest.main()
