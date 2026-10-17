# Blobs corpus engineering

This uv-managed Python project creates and verifies offline knowledge packages for Blobs. Python runs only on the development computer. Android consumes the resulting `.blobs` file and does not require Python, Git LFS, a server, or network access.

## Environment

```powershell
$env:UV_CACHE_DIR = "$PWD/.cache/uv"
uv sync --locked
uv run --locked python -m unittest discover -s tests
```

Python and dependency versions are pinned by `pyproject.toml` and `uv.lock`.

## Pipeline

The pipeline acquires a pinned source archive, extracts bounded records, normalizes article text and attribution, validates quality constraints, exports immutable record shards, builds a SQLite FTS5 payload, and packages that payload with an exact manifest.

Core commands are exposed through `python -m blobs_corpus.<module>`. Use `--help` on a module before running it. Builders refuse existing output editions and publish through a temporary path so a failed run cannot replace verified data.

Example package build from normalized records:

```powershell
uv run --locked python -m blobs_corpus.package library \
  artifacts/records/EDITION/manifest.json \
  artifacts/EDITION.blobs \
  --schema 3 \
  --id EDITION \
  --version VERSION \
  --title "Offline knowledge library" \
  --license "CC BY-SA 4.0" \
  --origin "SOURCE_URL"
```

Inspect the module help for the current schema and required metadata. Generated `.blobs` files and build workspaces are ignored by Git.

## Data integrity

- Inputs are pinned by origin, revision, byte length, and SHA-256.
- Normalized records retain canonical URL, contributor/history URL, source archive identity, and available revision identity.
- Extraction and passage lengths are bounded.
- SQLite integrity and FTS integrity checks run before publication.
- Package verification streams the complete payload and checks declared length, CRC, and SHA-256.
- Redirect and alternate-name resolution retains unresolved and ambiguous counts instead of guessing.

Large reviewed record shards under `artifacts/records/` use Git LFS. Model weights, raw downloads, extracted databases, generated packages, caches, and reports stay outside Git.

## Evaluation

[`evaluation/`](evaluation/README.md) contains reusable answer, retrieval, fixture, and native-evaluation tooling. Cases and model locks are versioned; run outputs are local and ignored. Evaluation results describe only the exact artifact, prompt, sampler, runtime, and hardware recorded for that run.

See [data notices](THIRD_PARTY_NOTICES.md) for Wikipedia attribution and host-side dependency licenses.
