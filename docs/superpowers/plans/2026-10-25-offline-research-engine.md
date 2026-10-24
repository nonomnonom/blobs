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

### Task 2: Evaluation on an available device

**Files:**
- Modify: `corpus/evaluation/README.md` only after the run is green.
- Results: `corpus/evaluation/results/` (git-ignored).

- [ ] **Step 1: Run the suite** with the 14-case `research-comparison-synthesis-v1` file and the pinned per-library retrieval cases, in airplane mode, with all eight libraries installed. Do not use a busy emulator that another session owns.
- [ ] **Step 2: Record** APK SHA-256, model file and size, each library edition, device/OS/ABI, per-turn source count, first-token and total timings, peak PSS, and the raw answers.
- [ ] **Step 3: Grade** with the published rubric. Report the pass count and every miss with the retrieved titles so the corpus gap is visible.
- [ ] **Step 4: Decide** the source count from evidence. If six harms quality or the turn budget on the default model, revert to the best measured value and record why; do not add a planner.
- [ ] **Step 5: Update** `docs/architecture.md` only with measured facts.
