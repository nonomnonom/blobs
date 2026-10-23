import unittest

from blobs_corpus.library import MAX_HEADING_CHARS, passage_spans


class PassageSpanTest(unittest.TestCase):
    def test_real_sections_still_split(self):
        body = "Intro text about the tool.\n\n== Removal ==\n\nTake the panel off carefully.\n\n== Reinstall ==\n\nClip it back."
        spans = list(passage_spans(body, "Door panel rattle"))
        self.assertEqual(spans[0]["path"], ["Door panel rattle"])
        self.assertTrue(any(span["path"][-1] == "Removal" for span in spans))
        self.assertTrue(any(span["path"][-1] == "Reinstall" for span in spans))

    def test_a_pasted_rule_is_content_not_a_heading(self):
        pasted = "== " + ("x" * (MAX_HEADING_CHARS + 5_000)) + " =="
        body = "First answer paragraph.\n\n" + pasted + "\n\nTrailing answer paragraph."
        spans = list(passage_spans(body, "Build failure"))
        self.assertTrue(all(len(span["path"]) == 1 for span in spans))
        self.assertTrue(all(len(__import__("json").dumps(span["path"])) <= 4000 for span in spans))
        rebuilt = "".join(body[span["start"] : span["end"]] for span in spans)
        self.assertIn("x" * 500, rebuilt)

    def test_long_but_plausible_heading_still_heads_a_section(self):
        heading = "y" * MAX_HEADING_CHARS
        body = f"Lead.\n\n== {heading} ==\n\nDetail text follows here."
        spans = list(passage_spans(body, "Title"))
        self.assertTrue(any(span["path"][-1] == heading for span in spans))


if __name__ == "__main__":
    unittest.main()
