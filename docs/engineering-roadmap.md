# Blobs engineering roadmap

**Status: active for subsequent work, 2026-10-23.** This roadmap directs the
Blobs rebuild from architecture through release evidence. It applies only to
`blobs/`. No source code, runtime, schema, executable, or asset from `blobs-ai`
is a dependency or a porting target.

## Outcome

Blobs must become a useful global offline research product: a user can install
large, high-quality libraries, ask a question without a network connection,
receive a local answer with inspectable sources, and manage library editions
without losing trust or storage control.

That outcome is not a bounty-win claim. Claims about answer quality, latency,
memory, offline behaviour, catalog scale, or release readiness require
reproducible evidence with an APK, exact model, exact library editions, device,
configuration, queries, raw responses, and method.

## Product planes

The product fails if any plane is treated as secondary.

| Plane | Product output | Current owner | Done only when |
| --- | --- | --- | --- |
| Local runtime | UI, turn lifecycle, FTS, bounded prompt, JNI completion, source reader, installer, cancellation | `android/app` | A target-device run proves the one fixed local research path |
| Knowledge supply | Source locks, reviewed records, SQLite FTS, `.blobs` archives, provenance, licences, retrieval cases | `corpus/` | A large library can be reproduced from declared inputs and validated before use |
| Asset delivery | Signed catalog, Hugging Face packages, bundled fallback catalog, APK, offline kit, release notes | `android/tools/`, `android/docs/`, `docs/` | Catalog, asset, APK, and release evidence point to the same immutable identities |
| Product quality | Retrieval suite, answer review, device acceptance, report schema | `corpus/evaluation/` and Android acceptance owners | Improvements are measured on a held-out, fixed package set |

Hugging Face is an optional asset-delivery channel. It must never be an
inference, retrieval, agent, or answer service after installation. A real upload
or release requires approved input assets, authorised credentials, protected
private keys, and the validation gate appropriate to that release. Do not
publish placeholders just to make progress appear complete.

## Data is a first-class product

Blobs is not a starter pack plus a language model. The durable advantage is a
large catalog whose records can be traced, updated, installed, searched, and
cited.

### Catalog build order

| Wave | Library family | Why it comes next | Admission criteria |
| --- | --- | --- | --- |
| 1 | Core reference | Establish broad global lookup coverage | Reproducible large source edition, redistribution rights, language/scope metadata, package and retrieval cases |
| 2 | Travel and regional | Support practical global research and place context | Country/region coverage plan, source-date policy, explicit gaps, source-reader attribution |
| 3 | Primary-domain | Improve trust where general reference is weak | Official/first-party source preference, defined freshness policy, claim boundaries |
| 4 | Structured local data | Serve narrow factual tasks such as facilities or named places | Frozen snapshot, reviewer sign-off, source-query provenance, no inferred volatile claims |
| 5 | User libraries | Allow user-owned knowledge without weakening catalog trust | Clear unverified origin state, safe import, local-only ownership |

Large library size is acceptable and expected. The app must expose archive size,
installed size, language, scope, edition, source snapshot date, update policy,
publisher, licence, limitations, and storage impact before installation. Each
edition remains immutable; a newer edition is a new installable identity and is
never a silent replacement.

The signed catalog is the public data contract. Each library entry has a
required `library_profile` with an exact record count, scope, source snapshot
date, update policy, and limitations. The app shows this profile rather than
turning a broad description into an unsupported quality or freshness claim.

`corpus/supply/registry.json` controls the portfolio before a pack reaches the
catalog. It gives core reference, travel, structured facts, and primary
guidance distinct supply lanes; keeps legacy artifacts development-only or
retired; and carries the exact allow-list consumed by the publisher. A planned
family is not a dataset or package. Only a release-ready entry, whose source
lock, rights review, reviewed records, retrieval cases, archive receipt,
`records_sha256`, archive digest, and `library_profile` match the catalog item,
can be published.

### Pack admission contract

No library enters the public catalog without all of the following:

1. a source lock with edition, origin, licence, scope, and acquisition identity;
2. raw or revision-pinned input retained by the authorised maintainer;
3. documented selection, normalization, and exclusion rules;
4. bounded records with per-document attribution and provenance;
5. SQLite FTS package, archive manifest, archive SHA-256, and `records_sha256`;
6. declared language, geographic scope, record count, archive size, publisher,
   update policy, known limitations, and required attribution;
7. versioned retrieval cases that exercise coverage and known misses; and
8. a rights review that covers distribution of source text and derivative form.

When one source edition is split into complementary volumes, every admission
also names the immutable partition plan and volume ID. The publisher compares
the plan digest and source-lock digest against the build receipt, preventing an
arbitrary topical subset from being labelled as one volume of the edition.

A place-data pack additionally needs the raw snapshot digest, exact query,
source timestamp, selection rule, reviewer identity/date/notes, and an
explicit statement of fields it cannot prove. It must not turn a keyword match
into “open now”, distance, route, popularity, safety, or best-choice claims.

### Data lifecycle

```text
source decision -> source lock -> raw snapshot -> review -> normalized records
                -> FTS library -> immutable archive -> signed catalog entry
                -> verified local install -> retrieval cases -> new edition
```

A failed acquisition, review, build, package validation, publication check, or
install must not replace a previous good edition. Raw input and temporary
artifacts remain outside Git unless they are intentionally versioned source
metadata. A historical observation must say so if the original input, corpus,
device, or executable can no longer be reproduced.

## Rebuild mode

The owner has chosen to rebuild engine, data flow, app structure, and corpus
pipeline before validation. Therefore **do not run builds, tests, benchmarks,
or evaluators** during this phase. Existing tests remain regression references;
they must not be altered just to appear green, and static inspection is not
runtime proof.

The rebuild phase ends only when one vertical slice is coherent: a verified
installed asset, local retrieval, one JNI completion, durable turn persistence,
cited evidence, and rendered state have clear owners. Validation then resumes
before performance, quality, offline, or release claims.

| Rebuild area | Direction | Current evidence state |
| --- | --- | --- |
| Research engine | `engine/LocalResearchEngine` owns one retrieval-to-completion pass | Changed or under reconstruction; not built or tested in this phase |
| Native runtime | `local_model_runtime.cpp` owns model lifecycle and decode; `KvCache` owns transient exact-prefix reuse; `blobs.cpp` is a JNI adapter | Static ownership/header inspection only; not built or tested in this phase |
| Dataset archive | `archive.py` owns the `.blobs` envelope; `package.py` carries `records_sha256` | Contract work only; no package-build claim |
| Transfer | `PackageTransferExecutor`, recovery, and `PackageTransferWork` own interrupted asset lifecycle | Contract work only; no device proof |
| Retrieval | SQLite boundary, validation, reader, aliases, and ranking are separated | Contract work only; no measured quality or latency |
| Persistence and UI | `ConversationStore`, Room migrations, scope snapshot, grounding, finish reason | Contract work only; no compiled-app claim |
| Knowledge catalog | Source locks, provenance, pack admission, and publication contract | Existing artifacts are inventory, not a claim of quality or completeness |

## Runtime invariants

A question follows one fixed local path:

```text
question -> FTS retrieval -> evidence -> bounded prompt -> one JNI completion
         -> citation validation -> persistence -> UI and source reader
```

Keep these invariants unless a new approved product decision replaces them:

- no Rust runtime, local HTTP server, socket, remote inference, or remote
  retrieval in the installed research path;
- no agent loop, tool loop, hidden retry, second retrieval, or second completion;
- one bounded FTS statement per selected library, then local ranking;
- evidence saved before completion begins;
- citations can identify only evidence saved by that turn;
- any cancellation preserves valid partial output and recorded evidence;
- catalog/download activity is explicit and separate from research;
- no data pack makes claims that its source and freshness metadata cannot support.

## Engineering sequence

| Stage | Deliverable | Acceptance intent |
| --- | --- | --- |
| A. Contracts | English product docs, owners, language policy, source and package contracts | Every behaviour and data invariant has one owner |
| B. Core data | Reproducible core-reference pack inventory and admission manifests | Pack scope, size, source rights, provenance, and gaps are reviewable |
| C. Device package path | Verified catalog download or user import to atomic activation | Old package survives failed/cancelled update |
| D. Retrieval baseline | Fixed cases per library and FTS coverage diagnosis | Same package gives explainable hit/miss results |
| E. Prompt and inference | One bounded prompt and one completion on native runtime | No hidden request path; cancellation has a terminal result |
| F. UX and source reading | Clear research states, citations, library management, source reader | Evidence is distinct from model prose |
| G. Target-device validation | Cold/warm, one-to-five turns, memory, storage, thermal, cancellation, offline | Results name device, OS, ABI, model, library, configuration |
| H. Quality iteration | Held-out retrieval and answer review | Improve a measured weakness without changing uncontrolled variables |
| I. Publication | Immutable library uploads, signed catalog revision, verified release candidate | Catalog and package digests agree |
| J. Release | Signed APK, offline kit, install/upgrade/rollback records, release notes | A user can reproduce the intended asset set |

Stages are ordered but may loop back when new evidence shows a regression.
Semantic retrieval, reranking, new model families, spatial indexes, and
recommendation scoring are not assumed requirements. They are experiments only
after the lexical baseline exposes a measured gap on the target device.

## Evaluation after rebuild

When validation is re-enabled, evidence is collected in layers:

| Layer | What it proves | What it does not prove |
| --- | --- | --- |
| Deterministic contract tests | Input, package, migration, cancellation, persistence, prompt, citation identity | World-fact correctness |
| Retrieval evaluation | Corpus coverage, recall@k, evidence selection, ranking regressions | Target-device latency or answer quality |
| Device acceptance | APK, native lifecycle, actual package install, offline flow, observed memory/timing | Performance on every Android device |
| Human answer review | Whether claims are supported by the recorded passage and useful for the task | General model intelligence |

Comparisons hold model, library SHA-256, case set hash, APK, device, OS, ABI,
network mode, context policy, output limit, and warm/cold policy fixed unless
one is the declared experiment variable. Do not call a desktop observation an
Android result, and do not call a result a competitor win if corpora, task,
rubric, or device differ.

## Immediate next work

1. Complete the dataset trust boundary: host record provenance and device
   library validation must agree on the same contract.
2. Inventory the existing large core-reference archives with complete catalog
   metadata and identify missing rights, source-lock, and retrieval-case inputs.
3. Define the first global catalog release set from quality and coverage, not
   from whichever pack is easiest to build.
4. Complete the single Android vertical slice without adding a second runtime
   path or service.
5. Once validation is explicitly enabled, establish package-specific retrieval
   baselines and target-device offline acceptance.
6. Use those results to remove the largest measured quality or latency
   bottleneck, then repeat the same suite.
7. Publish only verified immutable assets, sign the catalog, and create release
   evidence before making a bounty claim.
