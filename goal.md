# Blobs product goal

Build Blobs into a fast, trustworthy, offline Android research application that
can compete for the target bounty on its own merits. The app must answer from
user-installed local libraries, expose the exact local sources it used, and
remain useful when a library has no match. It must not depend on Blobs AI code,
Rust services, hosted agents, remote inference, or a local HTTP server.

## Product contract

- After a user installs assets, question answering, retrieval, source reading,
  conversations, and cancellation work without network access.
- A turn follows one fixed path: local FTS retrieval, a bounded prompt, one
  local JNI completion, citation-marker cleanup, persistence, and UI.
- Evidence is inspectable and belongs only to the turn that returned it.
  Citation markers establish source identity, not semantic proof.
- A retrieval miss never hides an otherwise usable answer; the model must be
  honest when a local source cannot support a claim.
- Users retain control of their conversations, installed assets, downloads, and
  storage. Asset verification and atomic installation protect existing data.

## Product boundary

The product is more than the Android inference screen. The delivery scope has
three connected planes, all owned in this repository:

- **Local research runtime:** Android UI, durable conversations, lexical
  retrieval, bounded prompting, one local model completion, source reading,
  installation, and cancellation.
- **Knowledge supply:** reviewed source locks, normalized records, reproducible
  SQLite FTS libraries, immutable `.blobs` archives, provenance, licenses, and
  retrieval cases.
- **Distribution and release:** a signed asset catalog, the Hugging Face
  dataset that hosts immutable packages, Android APK and offline-kit assembly,
  public documentation, and release evidence.

A new capability is incomplete if it changes one plane while leaving the
others unable to identify, distribute, verify, or evaluate it. Hugging Face is
an asset-distribution boundary, not an inference dependency: after a user has
installed assets, the research path remains local.

## Engineering direction

The current priority is a coherent rebuild of engine ownership, dataset/package
contracts, distribution contracts, application flow, and source structure.
Keep the runtime simple: one local retrieval and one model completion per
question, no agent loops or speculative services. Improve lexical retrieval,
prompt bounds, native streaming, cancellation, durable turn lifecycle, and
pack provenance before adding semantic retrieval or a reranker.

This is a rebuild phase. Do not claim that the app is fast, accurate, offline,
or bounty-ready until repeatable Android evidence records the APK, model,
library pack, device, configuration, query suite, and observed result. Until
the product owner explicitly re-enables validation, do not run builds, tests,
benchmarks, or evaluators. The detailed architecture and proof gates live in
[`docs/architecture.md`](docs/architecture.md) and
[`docs/engineering-roadmap.md`](docs/engineering-roadmap.md). The behavioral
target for turn provenance, renderable partial output, and structural grounding
is defined in [`docs/bounty-contract.md`](docs/bounty-contract.md).
