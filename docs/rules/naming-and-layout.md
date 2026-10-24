# Naming and layout

File and directory names must reveal their owned domain and role. Use concrete
domain nouns. Do not use catch-all names such as `utils`, `helpers`, `common`,
`shared`, `misc`, `temp`, or `new` for product logic.

## Primary layout

```text
android/app/src/main/
  kotlin/dev/blobs/app/
    data/          # Room, library, packages, orchestration
      transfer/    # download or URI through installed asset
      work/        # exclusive work and durable turn lifecycle
    engine/        # one retrieval pass to local completion
    feature/       # feature-specific UI
    inference/     # prompt, streamed answer, Kotlin JNI boundary
    ui/            # application composition and reusable UI
  cpp/             # JNI-called llama.cpp runtime
corpus/
  evaluation/      # suite, fixtures, and host evaluators
docs/
  rules/           # engineering rules
```

Kotlin follows language conventions: classes use `PascalCase`, functions and
properties use `camelCase`, and packages are lowercase. Documents, data files,
and commands use `kebab-case`. A pack name is a stable domain identity; snapshot,
format, and digest belong in its manifest rather than arbitrary UI names.

Tests live with the layer they prove. Kotlin unit tests use the unit-test source
set, Android acceptance tests use `androidTest`, and corpus evaluators live in
`corpus/evaluation`. Do not move evaluation fixtures or runtime reports into
the app as a condition of producing an answer.

When a refactor splits a file, move functions and their tests to the new
invariant owner. Preserve an external contract or document the breaking change
and migration for persisted data.
