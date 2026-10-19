import json
import tempfile
import unittest
from pathlib import Path

from evaluation.android.chat_performance import summarize


class AndroidChatPerformanceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.app_path = self.root / "installed-app.json"
        self.app_path.write_text(
            json.dumps(
                {
                    "complete": True,
                    "package": "dev.blobs.app.debug",
                    "version_name": "0.3.8",
                    "app_apk_sha256": "a" * 64,
                    "test_apk_sha256": "b" * 64,
                }
            ),
            encoding="utf-8",
        )
        self.runs = []
        for index, elapsed in enumerate((21000, 22000, 23000), start=1):
            path = self.root / f"run-{index}.json"
            path.write_text(
                json.dumps(
                    {
                        "run_id": f"run-{index}",
                        "complete": True,
                        "generation_started": True,
                        "finished_within_timeout": True,
                        "airplane_mode_enabled": True,
                        "status": "UNVERIFIED",
                        "application_id": "dev.blobs.app.debug",
                        "fingerprint": "test-device",
                        "model_sha256": "c" * 64,
                        "library_sha256": "d" * 64,
                        "wall_elapsed_ms": elapsed,
                        "first_visible_state_ms": elapsed - 5000,
                        "evidence_ready_state_ms": elapsed - 10000,
                        "performance": {
                            "retrieval_elapsed_ms": 5,
                            "native_generation": {
                                "total_us": 2_500_000,
                                "first_token_callback_us": 700_000,
                                "generation_loop_us": 2_000_000,
                                "prefill_us": 400_000,
                                "prompt_tokens": 500,
                                "output_tokens": 60,
                            },
                        },
                        "evidence_count": 2,
                        # Deliberately irrelevant: chat-path benchmarks do not grade
                        # retrieved titles or generated answers.
                        "evidence_sources": [{"title": "Unrelated", "publisher": "Elsewhere"}],
                    }
                ),
                encoding="utf-8",
            )
            self.runs.append(path)

    def tearDown(self):
        self.temp.cleanup()

    def test_summarizes_flow_latency_without_grading_answer_or_sources(self):
        report = summarize(self.runs, self.app_path)
        self.assertEqual(report["summary"]["elapsed_ms_median"], 22000)
        self.assertEqual(report["summary"]["elapsed_ms_p95_nearest_rank"], 23000)
        self.assertEqual(report["summary"]["evidence_ready_state_ms_median"], 12000)
        self.assertNotIn("expected_source", report)
        self.assertNotIn("expected_source_rank", report["runs"][0])
        self.assertEqual(report["summary"]["native_first_callback_ms_median"], 700)

    def test_requires_three_repeats_and_airplane_mode(self):
        with self.assertRaises(ValueError):
            summarize(self.runs[:2], self.app_path)
        run = json.loads(self.runs[0].read_text(encoding="utf-8"))
        run["airplane_mode_enabled"] = False
        self.runs[0].write_text(json.dumps(run), encoding="utf-8")
        with self.assertRaises(ValueError):
            summarize(self.runs, self.app_path)

    def test_refuses_saved_answer_text(self):
        run = json.loads(self.runs[0].read_text(encoding="utf-8"))
        run["answer"] = "Do not persist generated text in performance evidence."
        self.runs[0].write_text(json.dumps(run), encoding="utf-8")
        with self.assertRaises(ValueError):
            summarize(self.runs, self.app_path)


if __name__ == "__main__":
    unittest.main()
