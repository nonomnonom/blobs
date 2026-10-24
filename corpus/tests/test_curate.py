import unittest

from blobs_corpus.curate import exclusion


def page(path, body=""):
    return {"provenance": {"entry_path": path}, "body": body}


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

    def test_wikisource_keeps_works_and_drops_its_navigation(self):
        self.assertIsNone(exclusion(page("The_Wealth_of_Nations/Book_I"), "wikisource"))
        self.assertIsNone(exclusion(page("Authoring_a_Nation"), "wikisource"))
        self.assertEqual(
            exclusion(page("Author:Adam_Smith"), "wikisource"),
            "Author metadata page, not a primary text",
        )
        self.assertEqual(
            exclusion(page("Portal:American_Revolution"), "wikisource"),
            "Subject portal navigation, not a primary text",
        )
        self.assertEqual(
            exclusion(page("Main_Page"), "wikisource"),
            "Archive navigation page, not a primary text",
        )
        self.assertEqual(
            exclusion(page("Main Page"), "wikisource"),
            "Archive navigation page, not a primary text",
        )

    def test_wikisource_navigation_pages_are_excluded(self):
        self.assertEqual(
            exclusion(
                page("First_Geneva_Convention", "First Geneva Convention may refer to:\n\n* 1864"),
                "wikisource",
            ),
            "Disambiguation page, not a primary text",
        )
        self.assertEqual(
            exclusion(
                page(
                    "The_Canterbury_Tales",
                    "For works with similar titles, see Canterbury Tales.\n\n"
                    "Versions of The Canterbury Tales include:\n\n* Skeat",
                ),
                "wikisource",
            ),
            "Version index page, not a primary text",
        )

    def test_wikisource_works_with_hatnotes_and_late_lists_are_kept(self):
        self.assertIsNone(
            exclusion(
                page(
                    "First_Geneva_Convention_(1864)",
                    "For works with similar titles, see First Geneva Convention.\n\n"
                    "AMELIORATION OF THE CONDITION OF THE WOUNDED",
                ),
                "wikisource",
            )
        )
        self.assertIsNone(
            exclusion(
                page(
                    "A_History",
                    "The text begins in earnest.\n\n" + "x" * 600
                    + " Versions of this work include: a later printing.",
                ),
                "wikisource",
            )
        )

    def test_wikisource_chrome_rule_does_not_leak_to_other_projects(self):
        self.assertIsNone(exclusion(page("Author:Adam_Smith"), "wikipedia"))

    def test_support_paths_still_exclude(self):
        self.assertIsNotNone(exclusion(page("_mw_/MathML"), "wikipedia"))
        self.assertIsNotNone(exclusion(page("_assets_/logo.png"), "wikivoyage"))
        self.assertIsNotNone(exclusion(page("index"), "wikivoyage"))


if __name__ == "__main__":
    unittest.main()
