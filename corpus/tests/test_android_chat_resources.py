import unittest
import hashlib
import tempfile
from pathlib import Path

from evaluation.android.app_startup import parse_pss
from evaluation.android.chat_resources import (
    library_payload_sha256,
    decode_base64_text,
    package_source_arguments,
    parse_data_filesystem,
    parse_total_ram_kib,
)
from blobs_corpus.package import package


class AndroidChatResourceTests(unittest.TestCase):
    def test_parses_total_process_pss_from_dumpsys(self):
        output = """
                   TOTAL PSS: 91609       TOTAL RSS: 180123
        """
        self.assertEqual(91609, parse_pss(output))

    def test_rejects_missing_process_memory(self):
        with self.assertRaises(ValueError):
            parse_pss("TOTAL RSS: 180123")

    def test_uses_library_payload_digest_not_container_digest(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            payload = root / "library.sqlite"
            package_file = root / "library.blobs"
            payload.write_bytes(b"sqlite-payload-fixture")
            package(
                payload,
                package_file,
                kind="library",
                id="memory-fixture",
                version="1",
                title="Memory fixture",
                license="Test only",
                origin="local:test",
            )

            self.assertEqual(
                hashlib.sha256(payload.read_bytes()).hexdigest(),
                library_payload_sha256(package_file),
            )
            self.assertNotEqual(
                hashlib.sha256(package_file.read_bytes()).hexdigest(),
                library_payload_sha256(package_file),
            )

    def test_parses_android_ram_and_data_filesystem_capacity(self):
        self.assertEqual(
            7_923_456,
            parse_total_ram_kib("MemTotal:        7923456 kB\nMemFree: 123456 kB"),
        )

    def test_passes_installed_package_ids_before_instrumentation_runner(self):
        source_args = package_source_arguments(
            model_path=None,
            installed_model_key="gemma4-e2b-q4-675cff42-fa401b55b07ee70a",
            model_sha256="a" * 64,
            library_path=None,
            installed_library_key="nps-outdoor-safety-2026-10.2-c5ee800abfdd9c49",
        )

        self.assertEqual(
            [
                "-e", "installedModel", "gemma4-e2b-q4-675cff42-fa401b55b07ee70a",
                "-e", "installedLibrary", "nps-outdoor-safety-2026-10.2-c5ee800abfdd9c49",
            ],
            source_args,
        )

    def test_decodes_unicode_publisher_from_ascii_safe_cli_value(self):
        self.assertEqual(
            "National Park Service · U.S. Department of the Interior",
            decode_base64_text("TmF0aW9uYWwgUGFyayBTZXJ2aWNlIMK3IFUuUy4gRGVwYXJ0bWVudCBvZiB0aGUgSW50ZXJpb3I"),
        )
        with self.assertRaises(ValueError):
            decode_base64_text("%%%")
        self.assertEqual(
            {
                "size_kib": 12_223_704,
                "used_kib": 5_288_020,
                "available_kib": 6_415_044,
                "mount": "/data/user/0",
            },
            parse_data_filesystem(
                "Filesystem 1K-blocks Used Available Use% Mounted on\n"
                "/dev/block/dm-56 12223704 5288020 6415044 46% /data/user/0"
            ),
        )


if __name__ == "__main__":
    unittest.main()
