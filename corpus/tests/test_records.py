import unittest

from blobs_corpus.records import validate_document


class DocumentValidationTests(unittest.TestCase):
    def test_rejects_unicode_replacement_characters_in_every_text_field(self):
        valid = {
            "id": "source-1",
            "title": "Source title",
            "publisher": "Publisher",
            "url": "https://example.test/source",
            "license": "CC0",
            "updated": "2026-01-01",
            "saved": "2026-01-01",
            "body": "A source body with enough ordinary text.",
            "collection": "Test collection",
        }
        for field in valid.keys() - {"body"}:
            with self.subTest(field=field):
                record = dict(valid)
                record[field] = "source\ufffdtext"
                with self.assertRaisesRegex(ValueError, f"Invalid {field}.*replacement character"):
                    validate_document(record)

    def test_rejects_a_replacement_character_in_a_document_body(self):
        record = {
            "id": "source-1",
            "title": "Character encodings",
            "publisher": "Publisher",
            "url": "https://example.test/source",
            "license": "CC0",
            "updated": "2026-01-01",
            "saved": "2026-01-01",
            "body": "Upstream corruption leaves the Unicode replacement character \ufffd here.",
            "collection": "Test collection",
        }

        # LibraryValidator on Android refuses to install any library whose documents.body carries
        # U+FFFD, so the record contract cannot admit one either.
        with self.assertRaisesRegex(ValueError, "Invalid body.*replacement character"):
            validate_document(record)


if __name__ == "__main__":
    unittest.main()
