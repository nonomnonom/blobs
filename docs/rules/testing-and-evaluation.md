# Testing and evaluation

## Evidence boundary

Contract tests prove deterministic behaviour: input validation, prompt bounds,
response decode, cancellation, persistence, citation-marker identity, and asset
lifecycle. They do not prove that a model answers world facts correctly.

An evaluator measures retrieval or records a product artifact after the runtime
finishes a turn. It does not run a host HTTP server, agent loop, or model judge.
It must not turn an answer into an error merely because text differs from an
oracle or citation markers are incomplete. A non-empty body is retained so
correctness, grounding, and stop reason can be assessed separately.

## Rules

- Synthetic fixtures exist only to establish a valid contract; never present
  them as a production corpus or model answer target.
- Never hard-code real user questions, oracle titles, expected citation counts,
  or expected answers in the Android runtime.
- Manual end-to-end checks accept explicit model, library, and query inputs on
  the target device. They inspect lifecycle, output, evidence, and an artifact
  report; they do not force a particular answer or become an emulator/CI gate.
- Measure FTS changes with a fixed retrieval suite. Measure prompt or model
  changes with a held-out answer suite. Do not change corpus or device in the
  middle of a comparison.
- A native change needs the relevant ABI build and target-device acceptance when
  it changes loading, decoding, cancellation, KV cache, or memory behaviour.

See the [evaluation system](../evaluation-system.md) for required metrics and
recorded evidence.
