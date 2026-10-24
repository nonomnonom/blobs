# Architecture and ownership

## Principle

Separate code by the invariant it owns, not file size or technology name. A
long file is acceptable when it protects one coherent lifecycle. A small file
that mixes UI, persistence, retrieval, and native runtime is still misplaced.

## Blobs owners

| Area | Owns | Does not own |
| --- | --- | --- |
| `feature/` and `ui/` | Render state, user actions, navigation, accessibility | FTS queries, package file I/O, prompts, JNI |
| `WorkController` | Application action contract and selection of work lifecycle | Mutable work state, turn persistence, transfer lifecycle |
| `data/work/WorkSession` | One active job, answer/transfer exclusion, `WorkState` | Turn persistence, retrieval, package file operations |
| `data/work/ResearchWork` | Input validation, cancellation, checkpoints, one durable turn lifecycle | FTS ranking, prompt construction, native sampling, SQL implementation |
| `engine/` | One retrieval, bounded prompt, token stream, research result | Room writes, rendering, asset transfer |
| `data/local` | Room, DAO, migrations, durable conversation access via `ConversationStore`, UI-facing package state via `PackageStore` | Rendering and inference |
| `data/library` | Source access, scope, FTS, evidence candidates | Model lifecycle and UI policy |
| `data/packages` | Catalog, download, integrity, atomic installation | Prompt construction and ranking |
| `data/transfer` | URI or download through installation, interrupted-transfer recovery/discard, package transfer lifecycle through `PackageTransferWork` | UI state, turn persistence, source policy |
| `inference/` | Prompt budget, answer text, citation markers, Kotlin JNI boundary | Room queries and C++ decode |
| `app/src/main/cpp` | GGUF, model lifecycle, native `KvCache`, llama.cpp context, backend discovery/fallback, sampling, token decode, native stop | Retrieval, source selection, UI |
| `corpus/` | Pack format, ingestion, provenance, source locks, host evaluator inputs | Android runtime policy |

## Dependency direction

```text
feature/ui -> AppViewModel -> WorkController
WorkController -> data/work/ResearchWork, data/transfer/PackageTransferWork
ResearchWork -> WorkSession, data/local, engine
PackageTransferWork -> WorkSession, data/transfer executor and recovery
engine -> data/library, inference
inference -> native JNI
corpus tooling -> pack format and evaluator inputs
```

UI must not open packages or call JNI. Native code must not choose sources,
compose a product prompt, or read Room. Retrieval must not run a model to score
its ranking. For a cross-area change, begin at the correct owner contract and
connect layers one at a time.

## Creating a module

`data/work` exists because exclusive work and a durable answer-turn lifecycle
are a boundary distinct from both engine and transfer. `data/transfer` owns
asset movement and recovery; it must not take turn ownership.

Create a module only for an unowned lifecycle or trust boundary. Do not create
a single-implementation interface or catch-all folders such as `utils`,
`helpers`, `common`, `misc`, or `manager`. Name a module for the domain it
protects, such as `library`, `packages`, or `inference`.

## Change review

Before handoff, state the invariant owner, why the code is in that directory,
where each new dependency points, and which evidence proves it. If an invariant
crosses owners, document their contract instead of creating a global singleton.
