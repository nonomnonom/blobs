import gzip
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from blobs_corpus.web_pages import SameHostRedirect, extract, read_lock
from blobs_corpus.web_pages import _source_text


class PinnedWebPageTests(unittest.TestCase):
    def test_source_lock_rejects_external_hosts(self):
        with tempfile.TemporaryDirectory() as folder:
            lock_path = Path(folder) / "lock.json"
            lock_path.write_text(json.dumps({
                "format": 1,
                "id": "nps-outdoor-safety",
                "edition": "1",
                "sources": [{
                    "id": "test-page",
                    "host": "www.nps.gov",
                    "url": "https://example.com/page.html",
                    "title": "Test Page",
                    "updated": "2026-01-01",
                    "selector": "#article",
                    "body_sha256": "a" * 64,
                }],
                "publisher": "National Park Service",
                "license": "Public domain federal work",
                "saved": "2026-01-01",
                "collection": "Outdoor safety",
                "origin": "https://www.nps.gov/aboutus/disclaimer.htm",
            }), encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "Unapproved source origin"):
                read_lock(lock_path)

    def test_same_host_redirect_rejects_host_changes(self):
        handler = SameHostRedirect("www.nps.gov")
        with self.assertRaisesRegex(ValueError, "Unapproved source origin"):
            handler.redirect_request(None, None, 302, "Found", {}, "https://example.com/page")

    def test_extract_keeps_source_text_and_removes_page_furniture(self):
        article_text = "A tested source paragraph about outdoor preparation and safety. " * 8
        html = (
            "<html><body><h1>Outdoor Emergency Plan</h1><div id='article'>"
            "<nav>Navigation must not be copied.</nav><h2>Before the trip</h2>"
            f"<p>{article_text}</p><img alt='Photo credit that must not be copied'>"
            "<div class='Component RelatedGrid'>Related pages must not be copied.</div>"
            "</div><p>Last updated: January 1, 2026</p></body></html>"
        ).encode("utf-8")
        expected_body = f"== Before the trip ==\n\n{article_text.strip()}"
        source = dict(
            id="outdoor-emergency-plan",
            host="www.nps.gov",
            url="https://www.nps.gov/test/outdoor-emergency-plan.html",
            title="Outdoor Emergency Plan",
            updated="2026-01-01",
            selector="#article",
            body_sha256=hashlib.sha256(expected_body.encode("utf-8")).hexdigest(),
        )

        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            snapshots = root / "snapshots"
            snapshots.mkdir()
            (snapshots / f"{source['id']}.html").write_bytes(html)
            lock = dict(
                format=1,
                id="nps-outdoor-safety",
                edition="2026-10.1",
                publisher="National Park Service · U.S. Department of the Interior",
                license="No claim to original U.S. Government works.",
                saved="2026-10-19",
                collection="U.S. national park outdoor safety",
                origin="https://www.nps.gov/aboutus/disclaimer.htm",
                sources=[source],
            )
            lock_path = root / "lock.json"
            lock_path.write_text(json.dumps(lock), encoding="utf-8")
            output = extract(lock_path, snapshots, root / "records.jsonl.gz")
            repeated = extract(lock_path, snapshots, root / "repeated.jsonl.gz")
            identical = output.read_bytes() == repeated.read_bytes()

            with gzip.open(output, "rt", encoding="utf-8") as stream:
                record = json.loads(stream.readline())

        self.assertEqual(record["title"], "Outdoor Emergency Plan")
        self.assertIn("== Before the trip ==", record["body"])
        self.assertIn("outdoor preparation and safety", record["body"])
        self.assertNotIn("Navigation must not be copied", record["body"])
        self.assertNotIn("Photo credit", record["body"])
        self.assertNotIn("Related pages", record["body"])
        self.assertEqual(record["provenance"]["source_content_sha256"], source["body_sha256"])
        self.assertTrue(record["provenance"]["extractor_sha256"])
        self.assertTrue(identical)

        changed_text = html.replace(b"outdoor preparation", b"unreviewed claim", 1)
        with self.assertRaisesRegex(ValueError, "Reviewed source text changed"):
            _source_text(changed_text, source)
        changed_date = html.replace(b"January 1, 2026", b"January 2, 2026", 1)
        with self.assertRaisesRegex(ValueError, "Page update date changed"):
            _source_text(changed_date, source)

    def test_source_accepts_ready_gov_numeric_update_date(self):
        article_text = "An official emergency preparedness instruction. " * 8
        body = f"== Before a storm ==\n\n{article_text.strip()}"
        source = dict(
            id="emergency-kit",
            host="www.ready.gov",
            url="https://www.ready.gov/kit",
            title="Build A Kit",
            updated="2026-07-01",
            selector="#article",
            body_sha256=hashlib.sha256(body.encode("utf-8")).hexdigest(),
        )
        html = (
            "<html><body><h1>Build A Kit</h1><div id='article'>"
            f"<h2>Before a storm</h2><p>{article_text}</p>"
            "</div><p>Last Updated: 07/01/2026</p></body></html>"
        ).encode("utf-8")

        self.assertEqual(_source_text(html, source), ("Build A Kit", body))

        with self.assertRaisesRegex(ValueError, "Page update date changed"):
            _source_text(html.replace(b"07/01/2026", b"07/02/2026"), source)


if __name__ == "__main__":
    unittest.main()
