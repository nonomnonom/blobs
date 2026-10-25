# Wave A tree baseline, 2026-10-25

Captured before any Wave A code change, because Task 1 exists to make the later before/after
comparisons attributable to one tree.

## Where this wave lands

`Wave commits land on: feat/offline-research-engine` (worktree `.worktrees/offline-research-engine`,
HEAD `81af8c7`, working tree clean). It already carries the six-source breadth, the terminal digest
gate, the citation-content guard, the fourth model, and the published four-model catalog, and every
shelf capture quoted below came from its clean tree.

Main checkout `D:\rimbara\blobs` is on `release/0.4.0` at `823dfc9`.

## The plan documents are not committed

`docs/superpowers/specs/2026-10-25-blobs-speed-governor-design.md` and
`docs/superpowers/plans/2026-10-26-blobs-speed-wave-{a,b,c}.md` exist only as untracked files in the
main checkout. `git log -- docs/superpowers/plans docs/superpowers/specs` returns nothing, so there is
no hash to cite yet and the lane cannot reference them by commit. They need to be committed by their
author before Wave A's exit criteria can name them.

## Foreign uncommitted edits over Wave A's own files

`git status --porcelain android/app/src/main/cpp` in the main checkout, with
`git diff -w --ignore-blank-lines --stat` (210 insertions, 74 deletions):

- `cpp/CMakeLists.txt` (+6): GGML AVX/AVX2/FMA/F16C/BMI2 enabled for x86_64 — uncommitted
- `cpp/kv_cache.h` (+22/-…): `kKvDecodeBatchTokens` 256 → 512, plus cache-interface changes — uncommitted
- `cpp/kv_cache.cpp` (+50/-…): prepare/reuse changes — uncommitted
- `cpp/blobs.cpp` (+48/-…): thread guard `> 4` → `> blobs::kMaximumThreads` (8) — uncommitted
- `cpp/local_model_runtime.cpp` (+157/-…): runtime path including prefill and sampler — uncommitted
- `cpp/local_model_runtime.h` (+1): declaration for the above — uncommitted

Six files, not the three the plan anticipated, and they include the exact files Task 2 and Task 3 must
modify (`local_model_runtime.{h,cpp}`, `kv_cache.{h,cpp}`, `blobs.cpp`).

## Baseline for before/after comparison

**Blocked as written.** Wave A Task 1 Step 4 says: if these files are still dirty, do not continue.
Committing them from this lane would put two people's work in one commit, and discarding them would
destroy measured work (the AVX2 and batch-512 gains are the current best native configuration).

- Task 4 (prompt character budget) touches only `inference/ResearchPrompt.kt`,
  `engine/LocalResearchEngine.kt` and their JVM tests, none of which are dirty, so it proceeds.
- Tasks 2, 3 and 5 wait for the native edits to be committed by their author, or for an explicit
  instruction to take them over.

Until then, any native number produced on this branch is measured against `823dfc9` + the six edits
above, which is not a state that exists in git.
