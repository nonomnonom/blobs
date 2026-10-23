# Evidence, not a test suite

This directory holds inputs and tools for recording product evidence while
validation is active. It does not replace application contract tests and does
not permit a host HTTP server, agent loop, model judge, or generated answer to
become a product baseline.

## Validation status

Builds, tests, benchmarks, and evaluators may run when their prerequisites are
available. The commands and cases here define evidence contracts, not proof
that the current checkout has passed. Preserve active processes and data
preparation; record missing devices, packages, toolchains, credentials, and
reproduction inputs as blockers.

## Ownership

- `retrieval/` owns replay and smoke checks for pinned-package coverage. This is
  corpus/ranking diagnosis; it does not measure a phone or assign semantic value
  to a completion.
- `cases/` stores only retrieval queries, expected sources, and package
  identities. Never put target answers, rubrics, or product fixtures into the
  Android runtime.
- `device/` describes caller-controlled `@ManualCheck` capture on the target
  Android device. `NativeInferenceTest` uses explicit model and library files
  through the same product path.
- Run results live in an ignored operator-owned directory. Every result records
  APK, model, library, case set, device, configuration, and raw output or
  exception.

Host replay and an Android device answer different questions. Host replay can
identify package coverage regressions; only the target device can establish
offline lifecycle, cancellation, cache, or latency observations.

See the [evaluation system](../../docs/evaluation-system.md) for minimum
evidence and [device evaluation](device/README.md) for manual capture.
