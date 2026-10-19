import unittest

from evaluation.android.app_startup import parse_launch, parse_pss, percentile


class AndroidAppStartupTests(unittest.TestCase):
    def test_parses_successful_cold_activity_launch(self):
        result = parse_launch(
            "Starting: Intent { cmp=dev.blobs.app/.MainActivity }\n"
            "Status: ok\nLaunchState: COLD\nActivity: dev.blobs.app/.MainActivity\n"
            "TotalTime: 530\nWaitTime: 531\nComplete"
        )
        self.assertEqual(result["total_time_ms"], 530)
        self.assertEqual(result["wait_time_ms"], 531)

    def test_rejects_non_cold_or_failed_launch(self):
        with self.assertRaises(ValueError):
            parse_launch("Status: ok\nLaunchState: WARM\nTotalTime: 10\nWaitTime: 11")
        with self.assertRaises(ValueError):
            parse_launch("Status: error\nLaunchState: COLD\nTotalTime: 10\nWaitTime: 11")

    def test_parses_process_pss_in_kib(self):
        self.assertEqual(parse_pss("Native Heap: 100\nTOTAL PSS: 31988 TOTAL RSS: 142872"), 31988)
        with self.assertRaises(ValueError):
            parse_pss("No process found for: dev.blobs.app")

    def test_nearest_rank_percentile(self):
        self.assertEqual(percentile([6, 1, 4, 2, 5, 3, 7], 0.95), 7)
        self.assertEqual(percentile([1, 2, 3, 4], 0.50), 2)


if __name__ == "__main__":
    unittest.main()
