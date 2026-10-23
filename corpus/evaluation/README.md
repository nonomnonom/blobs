# Evidence, not a test suite

This directory holds inputs and tools for recording evidence after validation is
re-enabled. It does not replace application contract tests and does not permit a
host HTTP server, agent loop, model judge, or generated answer to become a
product baseline.

## Rebuild status

No evaluator, build, benchmark, or test runs during the current rebuild. The
commands and cases here are evidence contracts for the phase after the local
vertical slice is complete, not proof for an unvalidated checkout.

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
