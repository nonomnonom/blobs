# Evaluation tools

Optional host-side tools for checking retrieval behavior, answer structure, citation IDs, and package fixtures. They are separate from the Android runtime and are not required to build or use the app.

Install the pinned evaluation dependencies and run the corpus suite from this directory's parent:

```powershell
uv sync --locked --group evaluation
uv run --locked --group evaluation python -m unittest discover -s tests
```

Evaluation cases and model locks are versioned. Keep generated reports under `reports/`; they are local diagnostics and are excluded from source and release archives. Structural checks and retrieval-title matches do not establish answer correctness or source entailment. Review full answers against the cited passages before using the results to change a model, prompt, or retrieval policy.
