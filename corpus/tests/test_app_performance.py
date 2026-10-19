import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from evaluation.android.app_performance import capture


class AppPerformanceCaptureTest(unittest.TestCase):
    def test_rejects_shell_characters_before_contacting_adb(self):
        with patch("evaluation.android.app_performance.subprocess.check_output") as adb:
            for profile, model in [("bad;profile", "model-1"), ("phone-4gb", "model;reboot")]:
                with self.subTest(profile=profile, model=model), self.assertRaises(ValueError):
                    capture("adb", "emulator-5554", profile, model, Path("unused"))
            adb.assert_not_called()

    def test_refuses_incomplete_or_different_profile_evidence(self):
        for report in [
            {"complete": False, "profile": "phone-4gb"},
            {
                "complete": True,
                "profile": "another-phone",
                "responses": [{}] * 3,
                "searches": [{}] * 15,
            },
        ]:
            with self.subTest(report=report), tempfile.TemporaryDirectory() as directory:
                with (
                    patch(
                        "evaluation.android.app_performance.subprocess.check_output",
                        side_effect=self.adb_response(report),
                    ),
                    self.assertRaises(ValueError),
                ):
                    capture("adb", "emulator-5554", "phone-4gb", "model-1", Path(directory))
                self.assertFalse((Path(directory) / "phone-4gb.json").exists())

    def test_keeps_startup_samples_and_apk_identity_with_the_profile(self):
        report = {
            "complete": True,
            "profile": "phone-4gb",
            "responses": [{}] * 3,
            "searches": [{}] * 15,
        }
        with tempfile.TemporaryDirectory() as directory:
            with patch(
                "evaluation.android.app_performance.subprocess.check_output",
                side_effect=self.adb_response(report),
            ):
                saved = capture("adb", "emulator-5554", "phone-4gb", "model-1", Path(directory))
            self.assertEqual([42] * 5, saved["startup"]["runs_ms"])
            self.assertEqual(42, saved["startup"]["median_ms"])
            self.assertEqual("a" * 64, saved["apk_sha256"]["dev.blobs.app.debug"])
            self.assertEqual(
                saved,
                json.loads((Path(directory) / "phone-4gb.json").read_text(encoding="utf-8")),
            )

    @staticmethod
    def adb_response(report):
        def respond(command, **kwargs):
            operation = command[4:]
            if operation[:2] == ["pm", "path"]:
                return "package:/data/app/~~abc==/dev.blobs.app.debug-test/base.apk\n"
            if operation[:1] == ["sha256sum"]:
                return "a" * 64 + "  " + operation[1]
            if operation[:2] == ["am", "force-stop"]:
                return ""
            if operation[:2] == ["am", "start"]:
                return "Status: ok\nTotalTime: 42\n"
            if operation[:2] == ["am", "instrument"]:
                return "OK (1 test)\n"
            if operation[:1] == ["cat"]:
                return json.dumps(report)
            raise AssertionError(f"Unexpected ADB operation: {operation}")

        return respond


if __name__ == "__main__":
    unittest.main()
