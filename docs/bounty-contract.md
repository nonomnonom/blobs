# Blobs bounty contract

This document translates the bounty target into the Blobs Android behaviour
contract. It is an independent specification, not an adoption of another
product's code, runtime, Rust implementation, wire protocol, or repository
structure.

Status on 2026-10-23: this is rebuild direction, not proof that an APK, device,
model, or library already meets the contract. Validation remains paused under
[goal.md](../goal.md).

## One-turn outcome

After assets are installed, one question follows one fixed path:

```text
library scope snapshot -> local FTS retrieval -> evidence -> bounded prompt
                       -> one JNI completion -> marker validation
                       -> persistence -> UI
```

There is no agent loop, hidden retry, second retrieval, remote fallback, local
server, or second model call. Android UI does not need JSON compatibility with
another application. The following domain terms must be reflected in durable
turn state and export.

A persisted turn is either `running` or has exactly one terminal status. New
turns persist only the lifecycle status; the native outcome is preserved
separately as a finish reason.

| Status | Use | Answer body |
| --- | --- | --- |
| `complete` | A native completion or token-limited completion produces renderable text | Retained |
| `stopped` | User or lifecycle stops work before or after text arrives | Existing partial text is retained |
| `error` | Invalid input, invalid asset/runtime, or completion fails before renderable text | No new body is invented |
| `running` | A visible, cancellable preparation, retrieval, or completion state | Never presented as a terminal answer or error |

`token_limit` and a future `time_limit` are finish reasons, not errors, when a
non-empty body exists. The byte safety cap follows the same rule: valid output
is retained and the terminal metadata says that the turn was limited.

`cancelled`, `context_full`, and `error` are likewise finish reasons. Historical
turns may retain previous status strings such as `limited` or `interrupted`,
but the rebuild writes only the lifecycle statuses above.

## Evidence, citations, and grounding

Evidence is a local passage selected for one turn. Markers `[S1]`, `[S2]`, and
`[S3]` may refer only to evidence from that same turn. At finalization, the app
counts markers actually visible in the body and derives the citation list from
them. Retrieved evidence without a displayed marker is selection history, not a
citation that the UI may invent.

Grounding is a separate label.

| Value | Meaning |
| --- | --- |
| `grounded` | Each displayed factual prose sentence has a valid marker. This is structural coverage, not entailment proof. |
| `unverified` | The body may be useful but marker coverage is incomplete or retrieval did not support it. |
| `not_requested` | The turn intentionally ran without retrieval and does not claim grounding. |

No evidence, incomplete markers, or a retrieval miss must not turn a non-empty
model body into an error. The app must not add markers to conceal a model
failure. The source reader, export, and stored evidence preserve original text,
not prompt-escaped text.

## Immutable library scope

Before retrieval, the turn owner creates an immutable snapshot containing:

- user-requested scope and a flag distinguishing explicit empty scope from all
  ready libraries;
- the ready packages actually searched;
- requested packages that could not be searched, with structured reasons; and
- the immutable identity of each searched package: ID, edition, package digest,
  and record digest when its manifest supplies one.

The snapshot belongs to the turn, not the current UI setting. Changing selected
libraries during generation must not change active evidence, citations, or
export. Hashes provide reproduction and diagnosis; they are not required chat
content.

The rebuild stores package `id`, `version`, `SHA-256`, title, requested scope,
and `not_installed` or `disabled` reasons for unavailable selections. A library
builder carries normalized `records_sha256` into the manifest; Android preserves
it with the package and snapshot when present. Legacy packages and user-imported
libraries never fabricate a missing digest.

## Model lifecycle and streaming

Evidence may be available before the model is ready. When model preparation is
visible, the app may show evidence as reading material and `pending` state, but
must not render it as model prose or automatically resubmit the question. One
explicit user action starts one completion after the runtime is ready.

Streaming receives token bytes from JNI, stores a complete UTF-8 prefix inside
the output bound, and throttles UI rendering. Terminal state replaces temporary
text with the canonical persisted body. Cancellation in retrieval, evidence, or
generation finishes the turn safely without starting another completion.

A retry is a new explicit user action and a new durable turn. When retrying a
limited answer, Blobs excludes that preceding partial turn from the new
retrieval context and prompt history; it never turns a clipped answer into
hidden prompt state for its own replacement.

A future follow-up suggestion must be deterministic from the question, evidence,
and result. It cannot require a second completion or join the prompt/runtime.

## Implementation order

1. Define the domain model for scope snapshots, terminal citations, grounding,
   finish reason, and timing without overstating UI claims.
2. Migrate turn persistence explicitly. Historical turns remain readable as
   legacy records; they are never forced to `grounded`.
3. Connect the engine: create scope before FTS, checkpoint evidence immediately,
   validate markers at terminal state, and retain partial body.
4. Render state and source reader from immutable turn data, then export the same
   provenance.
5. After the rebuild restriction is removed, run prompt-boundary, retrieval
   hit/miss, cancellation, output limit, scope-change-during-turn, migration,
   and offline Android acceptance before making bounty claims.

A breaking change to turn data or export is acceptable when it reduces ambiguity,
but it needs a migration, a documented contract, and acceptance evidence after
validation resumes.
