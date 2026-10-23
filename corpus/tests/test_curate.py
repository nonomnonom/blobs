import unittest

from blobs_corpus.curate import exclusion


def page(path):
    return {"provenance": {"entry_path": path}}


class StackExchangeReviewTests(unittest.TestCase):
    def test_question_pages_are_kept(self):
        self.assertIsNone(
            exclusion(page("questions/12345/why-does-my-bike-chain-slip"), "stackexchange")
        )

    def test_site_chrome_is_excluded(self):
        self.assertEqual(
            exclusion(page("users/42/someone"), "stackexchange"),
            "Community member profile, not a question",
        )
        self.assertEqual(
            exclusion(page("tags/brakes"), "stackexchange"),
            "Tag directory page, not a question",
        )
        for path in ("questions", "questions/tagged/brakes", "questions_page=3", "about"):
            self.assertEqual(
                exclusion(page(path), "stackexchange"),
                "Site navigation or question listing, not a question",
                path,
            )

    def test_other_projects_keep_their_articles(self):
        self.assertIsNone(exclusion(page("users"), "wikipedia"))
        self.assertIsNone(exclusion(page("questions"), "wikivoyage"))

    def test_support_paths_still_exclude(self):
        self.assertIsNotNone(exclusion(page("_mw_/MathML"), "wikipedia"))
        self.assertIsNotNone(exclusion(page("_assets_/logo.png"), "wikivoyage"))
        self.assertIsNotNone(exclusion(page("index"), "wikivoyage"))


if __name__ == "__main__":
    unittest.main()
