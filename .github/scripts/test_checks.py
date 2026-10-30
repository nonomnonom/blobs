import json
import os
import tempfile
import unittest

from checks import affected, changeset_missing, gate_passes, pull_request_author


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


class ReleaseNoteCheckTest(unittest.TestCase):
    def test_shipped_components_require_a_changeset(self):
        for path in [
            "android/app/src/main/java/dev/blobs/app/App.kt",
            "corpus/blobs_corpus/build.py",
            "docs/web/content/index.mdx",
        ]:
            with self.subTest(path=path):
                self.assertTrue(changeset_missing([path]))

    def test_a_changeset_satisfies_the_requirement(self):
        self.assertFalse(
            changeset_missing(["android/app/build.gradle.kts", ".changeset/fix-search.md"])
        )

    def test_consuming_a_changeset_for_a_release_needs_no_new_note(self):
        self.assertFalse(changeset_missing([".changeset/fix-search.md", "CHANGELOG.md"]))

    def test_the_changeset_config_is_not_itself_a_release_note(self):
        self.assertTrue(changeset_missing(["docs/web/README.md", ".changeset/config.json"]))

    def test_repository_hygiene_changes_need_no_note(self):
        for path in [
            "README.md",
            "CONTRIBUTING.md",
            ".github/workflows/ci.yml",
            ".github/scripts/checks.py",
            "package.json",
        ]:
            with self.subTest(path=path):
                self.assertFalse(changeset_missing([path]))

    def test_dependency_updates_need_no_release_note(self):
        for author in ["dependabot[bot]", "dependabot"]:
            with self.subTest(author=author):
                self.assertFalse(changeset_missing(["docs/web/package-lock.json"], author))

    def test_a_human_change_to_a_dependency_file_still_needs_a_note(self):
        self.assertTrue(changeset_missing(["docs/web/package-lock.json"], "nonomnonom"))
        self.assertTrue(changeset_missing(["docs/web/package-lock.json"]))


class PullRequestAuthorTest(unittest.TestCase):
    def test_reads_the_author_from_the_event_payload(self):
        self.assertEqual(
            self.author_for({"pull_request": {"user": {"login": "dependabot[bot]"}}}),
            "dependabot[bot]",
        )

    def test_missing_payload_does_not_relax_the_gate(self):
        self.assertEqual(self.author_for({}), "")
        self.assertEqual(self.author_for(None), "")

    @staticmethod
    def author_for(payload):
        previous = os.environ.get("GITHUB_EVENT_PATH")
        if payload is None:
            os.environ.pop("GITHUB_EVENT_PATH", None)
            try:
                return pull_request_author()
            finally:
                if previous is not None:
                    os.environ["GITHUB_EVENT_PATH"] = previous
        handle, path = tempfile.mkstemp(suffix=".json")
        try:
            with os.fdopen(handle, "w", encoding="utf-8") as event:
                json.dump(payload, event)
            os.environ["GITHUB_EVENT_PATH"] = path
            return pull_request_author()
        finally:
            if previous is None:
                os.environ.pop("GITHUB_EVENT_PATH", None)
            else:
                os.environ["GITHUB_EVENT_PATH"] = previous
            os.unlink(path)


if __name__ == "__main__":
    unittest.main()
