# Starter library

`starter.documents.jsonl` contains 100 real Wikipedia articles captured on 17 October 2026. This is a small, named collection for science and everyday questions, not a complete encyclopedia. Each record includes its article revision address, contributor credit, dates, license, and plain text. `starter.lock.json` records the input SHA-256 and source inventory.

Text is adapted from Wikipedia contributors under **CC BY-SA 4.0**. Adaptations here are plain-text extraction and passage splitting. The original article and its revision history identify contributors. The license text is included in `../app/src/main/assets/licenses/CC-BY-SA-4.0.txt`. No Wikipedia logos or images are included. This data license is separate from the application code and original Blobs artwork.

Build the same captured collection without network access:

```sh
uv run --locked --project ../corpus python tools/build_package.py library corpus/starter.documents.jsonl packages/wikipedia-starter-schema3.blobs --schema 3 --id wikipedia-starter --version 2026-10-17.5 --title "Science and everyday questions" --license "CC BY-SA 4.0; adapted plain text; individual article attribution included" --origin "https://en.wikipedia.org/wiki/Wikipedia:Copyrights"
```

The builder refuses to overwrite an existing output. Inputs are streamed into SQLite, indexed with FTS5, then packaged with exact payload bytes and SHA-256. SQLite's output can differ across SQLite versions; the package manifest hashes the produced file. The captured JSONL is the reproducible content boundary.

`tools/prepare_wikipedia.py` is an explicit network-enabled refresh tool for the **development computer**. It writes a new local snapshot under ignored `packages/`; it never runs in the app. Review changed attribution and content before replacing the committed corpus. To build a larger or different collection, provide JSONL records with the same fields to `build_package.py`. Every source must have redistribution rights and attribution.
