# Offline research engine: one broad search, six cited sources

Date: 2026-10-25
Status: approved by the product owner; implementation in an isolated worktree.

## Decision

Keep the Perplexity-shaped experience that already ships and remove the bottleneck in it:

```text
question
  -> RetrievalQuery.forSearch / RetrievalQuery.match (existing term + phrase + title expansion)
  -> one FTS retrieval across every enabled library (existing LibraryStore boundary)
  -> up to 6 evidence passages, one per document before second passages
  -> one grammar-constrained completion over those 6 passages
  -> inline citation chips + a sources panel + suggested follow-ups (existing UI)
```

No question planner, no intent classifier, no entity/aspect decomposition, no second retrieval,
and no second completion. A comparison question is served the same way as any other question: the
existing lexical query already expands to `term OR phrase OR title:` clauses, and six result slots
let both named subjects in.

## Why

- **The experience already matches Perplexity.** `TurnCard` streams prose, `AnswerContent` renders
  numbered inline citations, `TurnSources` lists the cited passages, and suggestions follow. The
  missing piece was coverage, not chrome.
- **Three sources was the ceiling.** `ResearchPrompt.MAX_ANSWER_SOURCES = 3` limited both retrieval
  (`maximumResults`) and the grammar's marker set. Six is the largest value `LibraryStore.retrieve`
  accepts (`MAX_RETRIEVAL_RESULTS = 6`), so it is reachable without touching storage or ranking.
- **Six sources cost no extra prompt budget.** `LibraryStore` caps evidence at 6,000 characters
  total and 2,000 per passage, so six passages divide the same budget the three-source path already
  used. The native runtime allows 4,096 context tokens including 320 output tokens; the prompt stays
  well inside that.
- **Breadth comes for free in selection.** `selectEvidence` gives every named subject a slot, then
  takes one passage per distinct document before allocating extra passages, so six slots become six
  documents whenever six documents match.
- **A planner was the wrong layer.** The owner's directive is that the user experience should be
  Perplexity-like: ask, search, answer with sources. Internal intent rules add failure modes
  (lowercase names, unseen phrasing) without improving the visible experience.

## Trust gate

A finished completion that uses none of its sources must not be presented as fact. Measured on the
2 GB AVD, LFM2-1.2B ignored all six retrieved passages and produced a confident, uncited Japan
itinerary. The engine now applies one deterministic rule:

- if `FINISHED` and evidence is non-empty and the validated answer contains zero citations,
  `CitedDigest` replaces the prose with one verbatim, cited sentence per usable passage;
- the digest keeps each passage's own `[S#]`, so the citation opens the passage it came from;
- navigation lists (two or more `•` separated items) and fragments under 40 characters are skipped;
- cancellation and token-limited turns keep the model's partial prose, because the reader stopped
  that turn or saw it end;
- the digest is marked `SOURCED`, which structural coverage can then ground.

This gate adds no model call and no retrieval. It is the existing "citation validation" step from
the product contract, not a planner.

## Contract changes

- `ResearchPrompt.MAX_ANSWER_SOURCES`: 3 -> 6.
- System prompt citation instruction: `[S1]` to `[S6]`.
- The `StructuredAnswer` grammar marker set derives from `MAX_ANSWER_SOURCES`, so it admits
  `[S1]`..`[S6]` and still rejects `[S7]` and any other bracketed text.
- `LocalResearchEngine` already retrieves `maximumResults = MAX_ANSWER_SOURCES`; no code change is
  needed there.

Unchanged: retrieval SQL, ranking, 6,000-character evidence budget, prompt assembly, grammar shape,
decode, citation cleanup, grounding rules, persistence, and UI.

## Evidence from the current code

- `LibraryStore.retrieve(..., maximumResults)` accepts `1..6` and issues one FTS statement per
  library, then ranks locally.
- `ResearchPrompt` writes `<source id="S1">` blocks for every supplied source and caps each excerpt
  at 2,000 characters.
- `local_model_runtime.cpp` rejects a request when `prompt_tokens + output_tokens > 4096`.
- `AnswerText.citations` and `AnswerContent` already handle any marker index that has evidence.

## Evaluation

- Unit: the grammar contains `[S6]` and not `[S7]`; the prompt names the six-source range; existing
  prompt and decode tests stay green.
- Device (airplane mode, on an available emulator; a phone-class device when one exists): run the
  14-case comparison/synthesis suite and the pinned per-library retrieval cases. Record APK, model,
  libraries, device, plan-free turn path, source count, timings, and PSS.
- Decision rule: if six sources measurably reduce answer quality or exceed the turn budget on the
  default model, keep the change only where it wins and record the narrowed value; do not add a
  planner to compensate.

## Non-goals

- question planning, intent classification, entity decomposition, or multi-query retrieval;
- a reranker, embeddings, or a second model call;
- corpus, packaging, catalog, or model-pin changes;
- UI redesign; the existing citation and source components are the target experience.

## Risks

- Six weaker passages can dilute a tiny writer. The suite measures this directly; evidence budget
  and per-passage excerpts remain bounded.
- A comparison can still retrieve only one subject when the other is absent from the installed
  libraries. That is a corpus gap, reported honestly, not something a planner can fix.

## Sources

- `android/app/src/main/kotlin/dev/blobs/app/data/library/LibraryStore.kt`
- `android/app/src/main/kotlin/dev/blobs/app/inference/ResearchPrompt.kt`
- `android/app/src/main/kotlin/dev/blobs/app/inference/StructuredAnswer.kt`
- `android/app/src/main/cpp/local_model_runtime.cpp`
- `android/app/src/main/kotlin/dev/blobs/app/feature/chat/AnswerContent.kt`
