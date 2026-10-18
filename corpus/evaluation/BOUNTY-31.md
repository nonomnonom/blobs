# POIDH #31 evidence scorecard

The requirement snapshot is captured from the bounty's public JSON endpoint in [`requirements/poidh-mainnet-bounty-31-2026-10-18.json`](requirements/poidh-mainnet-bounty-31-2026-10-18.json). The endpoint is the evidence source for the scope below; this scorecard is Blobs' own engineering audit, not a bounty decision or claim of winning.

| Requirement | Current evidence | Status |
| --- | --- | --- |
| Android and compatible GrapheneOS | Native Android APK; no Google Play Services/Firebase dependency found in the app dependency graph. GrapheneOS hardware has not been exercised. | Android implemented; GrapheneOS device compatibility unverified |
| Maximum 12 GB RAM environment | Device-aware model recommendations exist. No sustained peak-RAM measurement is recorded for a full research answer. | Unverified |
| Maximum 50 GB for app, weights, indexes, databases and offline assets | The release calculator now sums every catalog option, including both raw Gemma GGUFs, the fallback model, starter library, and optional large library. All catalog payloads total 14,164,092,896 bytes; APK plus payloads total 14,188,629,910 bytes. Keeping every download beside every installed payload would total 25,140,994,369 bytes. This excludes Android APK expansion, app data, filesystem overhead and update coexistence. | Calculated with more than 35 GB margin; on-device accounting still needs confirmation |
| Fully offline after setup; no network/API/search during use | Source review shows network use in explicit package downloads; retrieval and inference paths use local SQLite and native model files. The retained app-level RAG smoke used a local GGUF fixture rather than installed model weights, so it does not prove a real-model offline session. A complete airplane-mode run has not yet been retained as release evidence. | Implemented by boundary; end-to-end real-model offline proof incomplete |
| No Google Play Services for core use | No Google Play Services or Firebase dependency appears in the Android app build. | Pass by dependency inspection |
| Useful explanation, comparison, synthesis and reasoning | Matched 40-case Windows CPU runs now compare Qwen3 1.7B and Gemma 4 E2B using the same current system prompt, Top 1M retrieval report, evidence, sampling, and llama.cpp revision. Both recorded 40/40 answers. Qwen emitted in-range citations in 40/40; Gemma did so in 38/40 and abstained without a citation in 2/40. Both share 33/40 any-title and 26/40 all-title retrieval diagnostics; these are routing targets, not answer-quality labels. Raw answers and passages are retained in [`results/`](results/). Source-level review already found retrieval and support failures, including the capillary case. | Family-neutral matched model baseline exists; no blind semantic score or internet-search reference comparison yet |
| Usable phone response speed | On the same Windows CPU host, Gemma 4 E2B median was 24.3 s total and 17.1 s to first content; Qwen3 1.7B was 28.5 s and 19.0 s. These are host timings, not phone predictions. Three native Gemma 4 E2B generation/cancellation smokes passed on an Android 16 x86_64 emulator. The 109.9 s app-level RAG smoke (85.0 s to first visible text) used a verified GGUF fixture without installing the model, so it is not real Gemma 4 RAG performance evidence. | Host comparison and model compatibility smoke recorded; real-model RAG speed, peak memory, and representative-device performance remain unmeasured |
| Public GitHub repository and reproducible build/data instructions | Public source, pinned `uv` environment, source archive, corpus preparation commands, package and APK verifiers, and Android CI are present. | Pass for source/build path; full corpus rebuild time and hardware variation remain documented |
| Real Android hardware at submission | All retained runtime results so far are emulator runs. | Not demonstrated yet |
| Public offline demo and POIDH submission proof | No public demonstration has been recorded in the repository. | Not demonstrated yet |
| More than 50% of internet search plus a frontier model | No blinded comparison against an internet-search/frontier reference set has been run. The 40-case result is not an official bounty score. | Not measured |

## Storage calculation

The all-catalog scenario intentionally treats the model choices as installable together, even though users may select only one. The calculation uses each pinned download byte count and each installed payload byte count from `android/app/src/main/assets/package-catalog.json`, plus the signed APK size. It also records a sequential import peak and a conservative case where every downloaded source file is retained. This is a bound over the offered catalog, not a filesystem measurement.

The selected files in the 0.3.1 offline kit are smaller than the full in-app catalog: they contain the fallback model and starter library. The Gemma models and large optional library are direct catalog downloads, so a two-package-only calculation must not be described as the product's maximum footprint.

## Answer and retrieval evidence

The earlier Qwen3 prompt-v2 run remains historical evidence and is not directly comparable with newer prompt versions. The matched current-prompt comparison is [`host-qwen3-1.7b-top1m-answer-v2-40-current-system-product-2026-10-18.json`](results/host-qwen3-1.7b-top1m-answer-v2-40-current-system-product-2026-10-18.json) versus [`host-gemma4-e2b-top1m-answer-v2-40-product-2026-10-18.json`](results/host-gemma4-e2b-top1m-answer-v2-40-product-2026-10-18.json). Both runs used the same 40 questions, retrieved evidence, current prompt, product sampler, and llama.cpp revision on a Windows CPU host with 34 GB RAM. Gemma's host median was 24.3 s total and 17.1 s to first content; Qwen's was 28.5 s and 19.0 s. All 40 Qwen answers contained in-range citations; Gemma had 38 answers with in-range citations and two uncited insufficiency responses. These mechanical results do not grade correctness or claim phone performance.

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
