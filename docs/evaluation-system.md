# Blobs evaluation system

Blobs evaluation proves one layer at a time. It does not run an agent, host HTTP
server, model judge, or host completion. The product has one assessed path:
local FTS, bounded prompt, one JNI completion, turn persistence, then UI and
source reader.

## Rebuild status

The product decision currently prohibits builds, tests, benchmarks, and
evaluators during the rebuild. This document defines evidence for the phase
after the owner re-enables validation; it is not a claim that the current
checkout has passed.

## Evidence owners

| Layer | Owner | Proves | Does not prove |
| --- | --- | --- | --- |
| Deterministic contract | Unit and instrumentation tests | Input validation, asset lifecycle, cancellation, persistence, UI state, citation-marker identity | Factual correctness or model quality |
| Retrieval | `corpus/evaluation/retrieval/` and pinned package cases | Corpus coverage, recall@k, selected evidence, ranking changes on the same package | Phone latency or answer quality |
| Offline Android product | `@ManualCheck` Android | One application path with named APK, model, library, configuration, and device | Behaviour or performance on another device |
| Human review | Maintainer review of raw output and passages | Claim support, citation usefulness, task quality | Automatic semantic truth |

`ManualCheck` does not run in the normal suite. `NativeInferenceTest` is the
acceptance for one retrieval and one JNI completion; multi-library, journey,
and package lifecycle checks record observations for caller-staged assets. No
emulator benchmark or package download is a CI gate.

## Evidence rules

- Retrieval cases are authored and versioned inputs. The Android runtime must
  not receive target answers, oracle titles, or required citation counts.
- Host replay diagnoses retrieval parity and coverage. Host timing cannot claim
  Android latency.
- The product records a non-empty body, truncated output, and cancelled output.
  Correctness, grounding, terminal status, and finish reason are assessed
  separately after the run.
- Citation markers prove identity only for evidence from the same turn; a
  reviewer compares claims with the actual passage.
- Compare one change at a time. Keep model, library hash, case set, APK, device,
  network mode, context, thread policy, output limit, and warm/cold policy fixed
  unless one is the declared experiment variable.

A comparable product or retrieval record must include commit/APK and test-APK
hash, model identity and SHA-256, library manifest and hash, case-set hash,
device/OS/ABI, inference configuration, UTC time, raw output, status/exception,
and skip reason. An observation without reproducible input, executable, corpus,
or device is **historical**.

## Gates after rebuild

| Change | Minimum evidence after validation is enabled |
| --- | --- |
| UI or turn state | Relevant state test and affected accessibility inspection |
| FTS or evidence selection | Same retrieval cases, same package hash, and coverage report |
| Prompt, model, or KV cache | `NativeInferenceTest` on target device; recorded evidence review |
| JNI, native lifecycle, or cancellation | Relevant ABI build and target-device acceptance with explicit model/library |
| Installer, pack, or storage | Integrity, recovery/rollback, and offline target-device acceptance |
| Public library catalog | Source/admission record, manifest, archive hash, signed catalog revision, install report |

See [device evaluation](../corpus/evaluation/device/README.md) for manual
capture and the [engineering roadmap](engineering-roadmap.md) for product
sequence.
