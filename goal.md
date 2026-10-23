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

The rebuild is not the product finish line. The active goal is to complete,
maintain, and prove every shipped Blobs surface: the Android application,
on-device inference and retrieval, library/model installation, conversation
storage, corpus and package pipeline, signed Hugging Face asset delivery,
release automation, and public documentation. Every advertised feature must
work end to end or be clearly identified as unavailable.

Keep the runtime simple: one local retrieval and one model completion per
question, with no agent loops or speculative services. Improve lexical
retrieval, prompt bounds, native streaming, cancellation, durable turn
lifecycle, and pack provenance based on measured failures before adding
semantic retrieval or a reranker. Treat the repository, dataset, package
catalog, and releases as maintained product surfaces, not one-time build
outputs.

The product owner explicitly re-enabled validation on 2026-10-23. Builds,
tests, benchmarks, evaluators, and device acceptance may now run when their
inputs and resources are available. Preserve other running processes and data
preparation. Record missing toolchains, devices, credentials, datasets, and
reproduction inputs as blockers instead of silently substituting evidence.

Do not claim that Blobs is production-ready, offline-verified, fast, accurate,
or bounty-ready until repeatable evidence records the APK, model, library
package, device and OS, configuration, query suite, and observed results.
Hugging Face uploads, signing-key use, GitHub releases, and other irreversible
publication actions require verified inputs and the required owner
authorization. The detailed architecture and proof gates live in
[`docs/architecture.md`](docs/architecture.md) and
[`docs/engineering-roadmap.md`](docs/engineering-roadmap.md). The behavioral
target for turn provenance, renderable partial output, and structural grounding
is defined in [`docs/bounty-contract.md`](docs/bounty-contract.md).
