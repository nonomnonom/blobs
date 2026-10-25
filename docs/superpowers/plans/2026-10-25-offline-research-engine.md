# Offline research engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Let one broad local search feed a six-source answer with the Perplexity-like citation experience that already ships, without a planner, a second search, or a second completion.

**Architecture:** Raise `ResearchPrompt.MAX_ANSWER_SOURCES` from 3 to 6. `LibraryStore.retrieve` already accepts six results and spends a fixed 6,000-character evidence budget, `StructuredAnswer` derives its marker set from the constant, and `LocalResearchEngine` already passes the constant as `maximumResults`. The change is one constant plus the system prompt's citation sentence, verified by grammar, prompt, and device tests.

**Tech Stack:** Kotlin, SQLite FTS5 through the existing `LibraryStore`, llama.cpp through `NativeBridge`, JUnit4, ktlint, Gradle.

**Spec:** `docs/superpowers/specs/2026-10-25-offline-research-engine-design.md`

## Global Constraints

- No network, local HTTP server, agent loop, tool loop, remote inference, or second retrieval in the research path.
- No question planner, intent classifier, or entity/aspect decomposition.
- Do not modify `LibraryStore.kt` or `RetrievalQuery.kt`; host replay is hash-pinned to them.
- Exactly one completion per turn and the existing `StructuredAnswer` grammar.
- Evidence budget stays 6,000 characters total and 2,000 per passage; prompt context stays under the native 4,096-token cap with 320 output tokens.
- Work only in `.worktrees/offline-research-engine` on branch `feat/offline-research-engine`.
- Run `./gradlew :app:testDebugUnitTest` and `./gradlew :app:ktlintCheck` before each commit.

---

### Task 1: Six-source answer breadth

**Files:**
- Modify: `android/app/src/main/kotlin/dev/blobs/app/inference/ResearchPrompt.kt`
- Test: `android/app/src/test/kotlin/dev/blobs/app/inference/StructuredAnswerTest.kt`
- Test: `android/app/src/test/kotlin/dev/blobs/app/inference/ResearchPromptTest.kt`

**Interfaces:**
- Consumes: `ResearchPrompt.MAX_ANSWER_SOURCES`, `StructuredAnswer.grammar`.
- Produces: `MAX_ANSWER_SOURCES = 6`; grammar markers `[S1]`..`[S6]`; system prompt citing `[S1]` to `[S6]`.

- [ ] **Step 1: Write the failing tests**

In `StructuredAnswerTest.grammarOwnsTheShapeTheDecoderExpects`, after the existing marker assertion, add:

```kotlin
assertTrue(StructuredAnswer.grammar.contains("\"[S6]\""))
assertFalse(StructuredAnswer.grammar.contains("\"[S7]\""))
```

In `ResearchPromptTest`, add:

```kotlin
@Test
fun theWriterIsToldTheWholeSourceRange() {
    assertEquals(6, ResearchPrompt.MAX_ANSWER_SOURCES)
    assertTrue(ResearchPrompt.system.contains("[S1] to [S6]"))
    assertFalse(ResearchPrompt.system.contains("[S3]"))
}
```

Add the missing `org.junit.Assert.assertFalse` / `assertTrue` imports if the file does not already
have them.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew --no-daemon :app:testDebugUnitTest --tests 'dev.blobs.app.inference.StructuredAnswerTest' --tests 'dev.blobs.app.inference.ResearchPromptTest'`

Expected: FAIL; `MAX_ANSWER_SOURCES` is still 3, the grammar has no `[S6]`, and the prompt still
says `[S1], [S2], or [S3]`.

- [ ] **Step 3: Implement the change**

In `ResearchPrompt.kt`, replace:

```kotlin
const val MAX_ANSWER_SOURCES = 3
```

with:

```kotlin
const val MAX_ANSWER_SOURCES = 6
```

and replace the citation sentence in `system`:

```kotlin
Use a source only when it supports the claim and cite it as [S1] to [S6]. Do not invent citations.
```

- [ ] **Step 4: Run the focused tests, the full unit suite, and lint**

Run: `./gradlew --no-daemon :app:testDebugUnitTest :app:ktlintCheck`

Expected: BUILD SUCCESSFUL, zero failures.

- [ ] **Step 5: Build the debug APK**

Run: `./gradlew --no-daemon :app:assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/dev/blobs/app/inference/ResearchPrompt.kt \
        android/app/src/test/kotlin/dev/blobs/app/inference/StructuredAnswerTest.kt \
        android/app/src/test/kotlin/dev/blobs/app/inference/ResearchPromptTest.kt \
        docs/superpowers/
git commit -m "feat(engine): answer from six cited sources in one search"
```

### Task 2: Uncited-answer trust gate

**Files:**
- Create: `android/app/src/main/kotlin/dev/blobs/app/inference/CitedDigest.kt`
- Test: `android/app/src/test/kotlin/dev/blobs/app/inference/CitedDigestTest.kt`
- Modify: `android/app/src/main/kotlin/dev/blobs/app/engine/LocalResearchEngine.kt`

**Interfaces:**
- Produces: `CitedDigest.build(evidence: List<Evidence>, maximumSources: Int = 6): String?`.
- Consumes: `AnswerText.citations`, `NativeBridge.FINISHED`, `ModelSupport`.

- [x] **Step 1: Write failing tests** for one cited line per passage, passages keeping their own
  `[S#]`, headings/fragments skipped, navigation lists skipped, duplicates collapsed, and no usable
  passage returning null.
- [x] **Step 2: Run the tests; expect unresolved reference.**
- [x] **Step 3: Implement** the 240-character, six-source digest and wire the engine rule: only a
  `FINISHED`, non-empty-evidence, zero-citation answer is replaced; cancellation and token-limit
  keep their partial prose.
- [x] **Step 4: Verify** unit tests, ktlint, debug and androidTest assembly.
- [x] **Step 5: Return to Task 3.**

### Task 3: Evaluation on an available device

**Files:**
- Modify: `corpus/evaluation/README.md` only after the run is green.
- Results: `corpus/evaluation/results/` (git-ignored).

- [ ] **Step 1: Run the suite** with the 14-case `research-comparison-synthesis-v1` file and the pinned per-library retrieval cases, in airplane mode, with all eight libraries installed. Do not use a busy emulator that another session owns.
- [ ] **Step 2: Record** APK SHA-256, model file and size, each library edition, device/OS/ABI, per-turn source count, first-token and total timings, peak PSS, and the raw answers.
- [ ] **Step 3: Grade** with the published rubric. Report the pass count and every miss with the retrieved titles so the corpus gap is visible.
- [ ] **Step 4: Decide** the source count from evidence. If six harms quality or the turn budget on the default model, revert to the best measured value and record why; do not add a planner.
- [ ] **Step 5: Update** `docs/architecture.md` only with measured facts.

## Measured results, 2026-10-25

Every run below is on the `blobs-e2e` emulator (2 GB RAM, 4 cores, API 36,
`sdk_gphone64_x86_64`), airplane mode on, debug app `dev.blobs.app.debug`, model
`lfm2-1b2-q4km-5399e76c-55175400e3f509a9` (730,893,248 bytes) and library
`wikivoyage-en-2026-09.5-380896b48d45f1d2` (622,776,320 bytes). Under the device
evaluation rules an emulator cannot carry a performance claim, so these are functional
observations only.

**Six sources with the trust gate (`run-6src-3`, `ui-report-3.json`).** The Japan synthesis
turn retrieved six passages and the model still produced zero citations, so the gate replaced
the hallucinated itinerary with six cited digest lines ("Sourced · 6 cited"). The Bangkok and
Hanoi comparison retrieved `Bangkok`, `Hanoi` and `Southeast Asia` as S1-S3 and answered with
one Bangkok sentence citing S4, a district page; Hanoi never appears. Emulator timings from the
same lane's captures were 53,214-76,541 ms to first visible text and 80,108-87,625 ms per turn;
`PublicResearchWorkflowTest` records no timings, so a timed re-capture is still owed.

**Singular query expansion, measured and rejected.** A candidate `QueryExpansion` appended
singular forms to the retrieval question only (never the prompt). On the device (`run-6src-4`,
`ui-report-4-expansion.json`) it fixed the retrieval miss it targeted: the Japan sources became
`Rail travel in Japan` (S1) and `Bus travel in Japan` (S2) and dropped `Pokémon Tour of Japan`,
while Bangkok/Hanoi was unchanged. Run against the exact published payload over the pinned
48-case `wikivoyage-en-retrieval-v3` set (`retrieval-48case-expansion-ab.json`), it moved 18
selections and 6 first passages, took expected-title hits from 45 to 43, improved nothing, and
regressed `hitchhiking-legality` and `thailand-gulf-beach-season` (`travellers` → `traveller`
crowd out `Hitchhiking`; `beaches` → `beach` drops `Thailand`). The change was reverted and the
retrieval policy stays frozen. The plural gap is an index property: `passages_fts` is built with
`tokenize='unicode61'`, so the fix belongs in the package build (a stemming tokenizer), which
means rebuilding and republishing every library, not rewriting the reader's question.

**Frozen replay blocker.** `corpus/evaluation/retrieval/host_replay.py` and `package_report.py`
refuse to run: committed `RetrievalQuery.kt` hashes to
`c5a6f0428e8391ef172720bd39a623165cf7759f0c436cf02cb5365132ebbb68` while the pin expects
`d2beec91da15db229894e0aabdf944678f03e60d1a6b3cdf3e156f37c7296625`. `LibraryStore.kt` still
matches its pin (`6c44e154b496ebe2f601c74583fd2f3f3224650dff77c60a64eab4acf122354a`). No commit
carries the pinned bytes: `f5eaab7` (the last commit touching the file) and both later
checkpoints hold `c5a6f042…`, `161f371` held `c6102dc4…`. The only copy matching the pin is an
uncommitted edit in the shared main checkout, made by another session, so it was left untouched
and the guard was not edited to make it pass. The A/B above therefore used the replay's own
functions (`retrieval_spec`, `direct_title_ids`, `retrieve`) on the verified package rather than
the guarded CLI, and no new reviewed replay has been recorded.

**One-sided comparison coverage, shipped.** The trust gate first fired only on a zero-citation
answer, so a Bangkok/Hanoi turn that cited one Bangkok district page stayed one-sided. The gate now
also tops up a finished turn that marked one source out of three or more: it keeps the model's prose
and appends up to two verbatim cited lines from sources it ignored, skipping every passage from the
guide the answer already used. `run-6src-5` showed the first version wasting one of the two lines on
a second Bangkok lead; `run-6src-6` (`run6-ui-report.json`) with the subject-aware rule answered the
same question with the model's Bangkok sentence `[S4]` plus `Hanoi` `[S2]` and `Southeast Asia`
`[S3]`, and every sentence in the turn carries a marker, which is the structural condition for
`GROUNDED`. The Japan synthesis turn still produces zero markers, so it still takes the full
six-line digest. Covered by `CitedDigestTest` (replace, top-up, subject skip, two-citation and
same-subject-only no-ops) and `./gradlew verify`.

**Still open, in priority order:** the writer is the binding constraint (a one-sentence
one-sided answer and a zero-citation answer both came out of six adequate sources on LFM2-1.2B);
12 of the 14 suite cases have never run through the UI.
The other seven intended libraries are built and on disk in `corpus/work/packages/`
(245 MB-1.7 GB each, about 7.8 GB in total) but none is installed on the test device.

**Why Task 3 is only two-fourteenths done, and what would unblock it.** Two measured limits, not
choices. First, `research-comparison-synthesis-v1.json` declares `intended_libraries` for the whole
suite and `PublicResearchWorkflowTest` refuses to start unless all of them are installed and
enabled, so no subset can run against a partial shelf; the fix is one small change (read an
optional per-case `intended_libraries`, fall back to the suite array) and it is deliberately not
written yet, because nothing can exercise it under the second limit: the host volume holding the
AVD has 7.0 GB free of 466 GB, staging `howto-repair` plus `howto-practical` alone needs about
790 MB of emulator storage, and `host_replay.unpack` refuses to start without the payload plus
1 GiB free. Timings and peak PSS are also still owed: `PublicResearchWorkflowTest` records no
per-turn timings, so the 53-76 s first-text and 80-88 s turn figures quoted earlier in this plan
carry no report of their own in this run set and must be treated as historical until re-captured
with the run id, APK and model that produced them.
The other seven intended libraries are built and on disk in `corpus/work/packages/`
(245 MB-1.7 GB each, about 7.8 GB in total) but none is installed on the test device.
