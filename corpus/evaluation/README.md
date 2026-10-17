# Evaluation tooling

This package contains reproducible checks for retrieval, answer structure, citation identity, package fixtures, and native model execution.

| Path | Purpose |
| --- | --- |
| `answers/` | Answer parsing, citation, structure, and support checks |
| `cases/` | Versioned evaluation questions and expected evidence constraints |
| `fixtures/` | Small deterministic inputs for tests and diagnostics |
| `locks/` | Exact model identity, source, size, hash, and license metadata |
| `native/` | Android staging and native-output audit helpers |
| `prompts/` | Versioned prompt experiments |
| `retrieval/` | Retrieval comparison and ranking tools |

Install the evaluation dependency group and run the test suite:

```powershell
$env:UV_CACHE_DIR = "$PWD/.cache/uv"
uv sync --locked --group evaluation
uv run --locked --group evaluation python -m unittest discover -s tests
```

Write local run output under `reports/`; that directory is ignored by Git. Do not overwrite a prior run. Record the model hash, prompt/template revision, sampler, runtime revision, input evidence, hardware/ABI, timings, and cancellation/timeout behavior with each result.

Mechanical checks can validate source-ID syntax, supplied-source bounds, retrieval ordering, and configured thresholds. They do not establish factual support by themselves. Review claim-to-source support, contradiction, insufficiency handling, and misleading citation placement before promoting a model or prompt.
