import unittest

from evaluation.android.app_startup import parse_pss


class AndroidChatResourceTests(unittest.TestCase):
    def test_parses_total_process_pss_from_dumpsys(self):
        output = """
                   TOTAL PSS: 91609       TOTAL RSS: 180123
        """
        self.assertEqual(91609, parse_pss(output))

    def test_rejects_missing_process_memory(self):
        with self.assertRaises(ValueError):
            parse_pss("TOTAL RSS: 180123")


if __name__ == "__main__":
    unittest.main()
