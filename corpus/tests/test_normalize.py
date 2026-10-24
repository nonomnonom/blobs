import unittest

from blobs_corpus.normalize import article


STACK_EXCHANGE_LOCK = {
    "id": "stackexchange-mechanics-en-all",
    "edition": "2026-10",
    "sha256": "0" * 64,
    "wiki_host": "mechanics.stackexchange.com",
    "publisher": "Stack Exchange network contributors",
    "collection": "Stack Exchange · Mechanics",
    "license": "CC BY-SA 4.0; Stack Exchange contributors",
    "verified_date": "2026-10-24",
}

PAGE = b"""<html><body>
<header>Questions Tags Users About</header>
<div id="question" data-questionid="42" data-score="7">
  <h1>How do I replace a brake pad?</h1>
  <div class="post-signature"><div class="s-user-card">
    <a class="s-user-card--link">Asker</a>
    <time datetime="asked Jan 1">asked Jan 1</time>
  </div></div>
  <div class="js-post-body"><p>The pad is worn down to the backing plate.</p></div>
</div>
<div class="answer accepted-answer" data-answerid="9" data-score="12">
  <div class="post-signature"><div class="s-user-card">
    <a class="s-user-card--link">Helper</a>
    <time datetime="answered Jan 2">answered Jan 2</time>
  </div></div>
  <div class="js-post-body"><p>Remove the caliper bolt and slide the pad out.</p></div>
</div>
<div class="answer" data-answerid="10" data-score="1">
  <div class="js-post-body"><p>Check the rotor thickness first.</p></div>
</div>
</body></html>"""


class StackExchangeArticleTests(unittest.TestCase):
    def test_question_keeps_site_url_and_post_history(self):
        document = article(
            PAGE,
            "How do I replace a brake pad?",
            "questions/42/how-do-i-replace-a-brake-pad",
            STACK_EXCHANGE_LOCK,
        )
        self.assertEqual(
            document["url"],
            "https://mechanics.stackexchange.com/questions/42/how-do-i-replace-a-brake-pad",
        )
        self.assertEqual(
            document["provenance"]["history_url"],
            "https://mechanics.stackexchange.com/posts/42/revisions",
        )
        self.assertIn("https://mechanics.stackexchange.com/posts/42/revisions", document["license"])

    def test_answers_keep_attribution_and_leave_site_chrome_out(self):
        document = article(
            PAGE,
            "How do I replace a brake pad?",
            "questions/42/how-do-i-replace-a-brake-pad",
            STACK_EXCHANGE_LOCK,
        )
        body = document["body"]
        self.assertNotIn("Questions Tags", body)
        self.assertIn("Asked by Asker", body)
        self.assertIn("2 answers", body)
        self.assertIn(
            "== Answer 1 · score 12 · accepted · by Helper · answered Jan 2 · a/9 ==", body
        )
        self.assertIn("Remove the caliper bolt", body)
        self.assertIn("== Answer 2 · score 1 ·", body)
        self.assertIn("Check the rotor thickness first.", body)
        self.assertEqual(document["provenance"]["normalization"]["answers"], 2)
        self.assertEqual(document["provenance"]["normalization"]["question_id"], "42")

    def test_missing_question_identity_is_rejected(self):
        with self.assertRaises(ValueError):
            article(
                b"<html><body><div id='question'><div class='js-post-body'>Text</div></div></body></html>",
                "Title",
                "questions/1/title",
                STACK_EXCHANGE_LOCK,
            )


if __name__ == "__main__":
    unittest.main()
