import unittest

from checks import affected, gate_passes


class CheckSelectionTest(unittest.TestCase):
    def test_community_only_changes_skip_component_builds(self):
        self.assertEqual(
            affected(["README.md", "CONTRIBUTING.md", ".github/ISSUE_TEMPLATE/bug_report.yml"]),
            {"android": False, "docs": False},
        )

    def test_application_corpus_and_build_policy_select_android(self):
        for path in [
            "android/app/build.gradle.kts",
            "corpus/uv.lock",
            ".clang-format",
            "ruff.toml",
            ".editorconfig",
        ]:
            with self.subTest(path=path):
                self.assertEqual(affected([path]), {"android": True, "docs": False})

    def test_website_changes_select_docs(self):
        self.assertEqual(affected(["docs/web/package-lock.json"]), {"android": False, "docs": True})

    def test_shared_inputs_and_workflow_changes_select_both(self):
        for path in [
            "android/app/src/main/res/font/font.ttf",
            "android/app/src/main/assets/package-catalog.json",
            ".github/workflows/ci.yml",
            ".github/scripts/checks.py",
        ]:
            with self.subTest(path=path):
                self.assertEqual(affected([path]), {"android": True, "docs": True})

    def test_gate_accepts_successes_and_intentionally_skipped_components(self):
        for android in [True, False]:
            for docs in [True, False]:
                self.assertTrue(gate_passes(self.results(android, docs)))

    def test_gate_rejects_failed_missing_cancelled_or_unexpectedly_skipped_work(self):
        for component in ["changes", "android", "docs"]:
            for outcome in ["failure", "cancelled", "skipped", None]:
                with self.subTest(component=component, outcome=outcome):
                    results = self.results(True, True)
                    results[component]["result"] = outcome
                    self.assertFalse(gate_passes(results))
        self.assertFalse(gate_passes({}))
        results = self.results(False, False)
        results["changes"]["outputs"].pop("android")
        self.assertFalse(gate_passes(results))

    @staticmethod
    def results(android, docs):
        return {
            "changes": {
                "result": "success",
                "outputs": {"android": str(android).lower(), "docs": str(docs).lower()},
            },
            "android": {"result": "success" if android else "skipped"},
            "docs": {"result": "success" if docs else "skipped"},
        }


if __name__ == "__main__":
    unittest.main()
