# POIDH #31 evidence scorecard

The requirement snapshot is captured from the bounty's public JSON endpoint in [`requirements/poidh-mainnet-bounty-31-2026-10-18.json`](requirements/poidh-mainnet-bounty-31-2026-10-18.json). The endpoint is the evidence source for the scope below; this scorecard is Blobs' own engineering audit, not a bounty decision or claim of winning.

| Requirement | Current evidence | Status |
| --- | --- | --- |
| Android and compatible GrapheneOS | Native Android APK; no Google Play Services/Firebase dependency found in the app dependency graph. GrapheneOS hardware has not been exercised. | Android implemented; GrapheneOS device compatibility unverified |
| Maximum 12 GB RAM environment | Device-aware model recommendations exist. No sustained peak-RAM measurement is recorded for a full research answer. | Unverified |
| Maximum 50 GB for app, weights, indexes, databases and offline assets | The release calculator now sums every catalog option, including both raw Gemma GGUFs, the fallback model, starter library, and optional large library. All catalog payloads total 14,164,092,896 bytes; APK plus payloads total 14,188,629,910 bytes. Keeping every download beside every installed payload would total 25,140,994,369 bytes. This excludes Android APK expansion, app data, filesystem overhead and update coexistence. | Calculated with more than 35 GB margin; on-device accounting still needs confirmation |
| Fully offline after setup; no network/API/search during use | Source review shows network use in explicit package downloads; retrieval and inference use local SQLite and native model files. The cited RAG run used local model and corpus files. A complete airplane-mode run has not yet been retained as release evidence. | Implemented by boundary; end-to-end offline proof incomplete |
| No Google Play Services for core use | No Google Play Services or Firebase dependency appears in the Android app build. | Pass by dependency inspection |
| Useful explanation, comparison, synthesis and reasoning | `answer-validation-v2-40.json` has 40 questions across five categories. The Qwen3 1.7B host outputs are retained in [`results/`](results/); exact title presence is 33/40 and is only a retrieval diagnostic. The app now rejects completed answers whose cited sentences lack a minimum lexical match to the referenced passages. This catches obvious mismatches but cannot prove semantic support. | Benchmark exists and a lexical fail-closed guard is implemented; source fidelity and answer quality remain unproven |
| Usable phone response speed | Qwen3 1.7B host median: 27.0 s total and 17.1 s to first content. One Gemma 4 E2B Android emulator RAG run took 109.9 s end to end, with first visible answer text at 85.0 s. Host timing is not phone timing; the emulator result exposes a real long-context latency risk. | Needs performance work and representative-device measurement |
| Public GitHub repository and reproducible build/data instructions | Public source, pinned `uv` environment, source archive, corpus preparation commands, package and APK verifiers, and Android CI are present. | Pass for source/build path; full corpus rebuild time and hardware variation remain documented |
| Real Android hardware at submission | All retained runtime results so far are emulator runs. | Not demonstrated yet |
| Public offline demo and POIDH submission proof | No public demonstration has been recorded in the repository. | Not demonstrated yet |
| More than 50% of internet search plus a frontier model | No blinded comparison against an internet-search/frontier reference set has been run. The 40-case result is not an official bounty score. | Not measured |

## Storage calculation

The all-catalog scenario intentionally treats the model choices as installable together, even though users may select only one. The calculation uses each pinned download byte count and each installed payload byte count from `android/app/src/main/assets/package-catalog.json`, plus the signed APK size. It also records a sequential import peak and a conservative case where every downloaded source file is retained. This is a bound over the offered catalog, not a filesystem measurement.

The selected files in the 0.3.1 offline kit are smaller than the full in-app catalog: they contain the fallback model and starter library. The Gemma models and large optional library are direct catalog downloads, so a two-package-only calculation must not be described as the product's maximum footprint.

## Answer and retrieval evidence

The 40-case Qwen3 host run is useful as a retained baseline, not as a product result: 40/40 outputs were recorded in the prompt-v2 run, all had mechanically in-range source IDs, and the median host latency was 27.0 s. The earlier host run recorded 39/40 and had a 32.0 s median. The runs are on Windows CPU with 34 GB host memory, use a Qwen3 1.7B model rather than the selected Gemma app model, and have no blind semantic grading.

The Top 1M native retrieval result found every expected title in 26/40 cases and at least one expected title in 33/40. Expected titles are routing targets, not relevance labels. Source-level review of the retained answers exposed weak support on cases such as capillary action and tank-flow arithmetic. The next quality gate is a blinded, case-by-case review that compares answer correctness, completeness, reasoning, and source support against an internet-search/frontier reference, while preserving failures and latency.

Re-export a local host run into the checked-in results format with:

```powershell
cd corpus
uv run --locked python -m evaluation.answers.export_host_run `
  --run reports/runs/YOUR_RUN.json `
  --cases evaluation/cases/answer-validation-v2-40.json `
  --output evaluation/results/YOUR_NEW_RESULT.json
```

The exporter refuses to overwrite outputs, preserves failures and cited passages, records model/runtime/prompt/sampling hashes, and reports only mechanical checks. It does not grade semantic support or the bounty's 50% threshold.
