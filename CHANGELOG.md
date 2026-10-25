# CHANGELOG

## Unreleased

- Turn attribution: every stage of a completion (model load, chat template, tokenize, context prepare,
  prefill, grammar, sampler, first token) is now timed in native code, carried across JNI, and written
  into device captures together with the APK digest, fingerprint, API, ABI and thread count of the run.
  The prompt gained a real 6,000-character bound — `docs/architecture.md` had described one that no
  branch ever implemented — and it now sheds retained history before it cuts any passage, so a squeezed
  shelf still answers instead of failing. Context capacity stopped doubling per turn, so the KV context
  survives between turns on the same model.
- Measured on the 2 GB emulator profile (LFM2-1.2B, one library, four turns, records
  `wavea-e2e-ui-1` and `wavea-probe-ui-1`): prefill costs 55.7-58.7 ms per prompt token and is the
  whole wait, while prefix reuse returns zero even for a byte-identical prompt — a defect in
  `KvCache::reuse_prefix`, now the largest known speed item. `device_acceptance: unverified`: no
  phone-class device has run these captures.

## 0.4.0

### Minor Changes

- Catalog delivery: the app's download list is now compiled from reviewed supply admissions
  instead of a hand-kept asset. `android/tools/build_catalog.py` reads
  `corpus/supply/registry.json` together with `android/models.lock.json` and the new
  `android/models.present.json`, pulls each library's manifest, digests, sizes, and
  `library_profile` from the archive receipt recorded with the admission, and validates the result with the
  same admission contract that gates an upload. `--check` prints how the shipped catalog drifts
  from the registry. The committed catalog now carries the nine admitted libraries and their
  `records_sha256` identities, so this release downloads only editions that passed the review,
  package, case, rights and digest gates.

  Catalog signing moved to a freshly generated P-256 pair chosen by the product owner on
  2026-10-24, because no private half of the previous key existed on any maintained machine. The
  app now verifies against `catalog-public-key-v3.pem` and still accepts a catalog signed with
  `catalog-public-key-v2.pem`, which authenticates the currently published revision. An install
  older than this build rejects a v3-signed catalog and must be updated to keep receiving
  catalog refreshes.

  Model publication is now an explicit per-model declaration: `android/models.present.json` carries a `publication` state and the compiler refuses to build a catalog when a locked model declares none. All three locked models are `authorized` (`qwen35-2b-q4km`, `phi4-mini-q4km`, and `lfm2-1b2-q4km`), so the catalog this release ships, and the list it downloads, matches the committed declarations. Because the state is per model, a locked candidate cannot reach the catalog by editing `models.lock.json` alone; the LFM Open License text that model requires travels in `assets/licenses/`.

- 133653d: Chat transcript: the reader owns the scroll. A new turn anchors near the top with a peek of
  the previous turn, a streaming answer is followed only while the reader is already at the
  live edge, and a saved thread reopens on its last anchored turn instead of the raw bottom.

  Turn states now share one step shape (`Trace`, `TraceLine`, `TraceCollapsible`): the live
  research stage, a finished turn's measured passages, citations, and elapsed time, the
  evidence list, and grounding. Grounding changes from a single caption into an expandable
  "How this answer was checked" chain whose every line is a fact this turn recorded — scope
  searched, passages found, time taken, passages carrying a marker, the model's own support
  claim, and the structural verdict. Blobs runs one local completion, so the chain shows no
  model reasoning and invents none.

- Inference runtime performance follows llama.cpp's own tuning guidance. Threads are now
  auto-detected from the device (clamped to 8) instead of a fixed four-thread policy, so
  eight-core phones use all cores for prompt processing and generation. Prompt decoding runs
  in 512-token batches with a 256-token physical ubatch. x86_64 builds enable AVX/AVX2/
  FMA/F16C/BMI2 paths that ggml dispatches from CPUID at runtime; arm64 builds keep the
  pinned KleidiAI kernels, and the KV cache keeps Q8_0 storage with Flash Attention on
  llama.cpp's automatic selection.
- A third model joins the in-app list: LFM2 1.2B Q4_K_M, the official Liquid AI GGUF at
  a pinned revision, about 0.73 GB against 1.28 GB for the current default. On the
  acceptance emulator it was the faster and lighter of the two on every measured axis —
  50.3 against 92.3 milliseconds per token while grammar-constrained, five tapped-offer
  turns at 7.4-10.9 s against 15.9-29.2 s, and 1.30 against 1.89 GiB of resident memory
  after a turn, which is the difference that matters on a 4 GB phone. Qwen3.5 2B stays the
  default and the advertised starting point on any device that meets its declared memory
  floor; on a device that cannot hold it, the welcome recommendation now resolves to the
  model that can actually load there instead of the one that would fail.

  The download points at Liquid AI's own repository, so no copy of the weights is
  redistributed by this project. Its row carries Liquid AI's own mark beside the Qwen and
  Microsoft marks, downloaded unchanged from the publisher and provenance-recorded with
  the others. That license is the LFM Open License 1.0 rather than
  Apache, so its full text now ships in the app and appears under Settings → About, and
  its Section 5 conditions Commercial Use on staying below 10,000,000 USD of annual
  revenue. No emulator figure here is a phone claim; both models still answer without
  inline citation markers on the offer path, which is recorded as a model-capability
  blocker rather than fixed by a stricter mask.

- Library packaging: an archive the app would refuse to install can no longer be built.
  `corpus/blobs_corpus/runtime_contract.py` mirrors the document and passage invariants that
  `LibraryValidator` enforces on device - text type, per-column length limits, and the absence of
  NUL and U+FFFD characters - and `blobs_corpus.package` audits the database before writing any
  `.blobs` file, naming the offending document ids when it stops.

  This closes a gap that hid a real defect: several admitted editions carry U+FFFD characters
  inherited from their pinned upstream archives, which their rights reviews recorded as an
  accepted limitation. A host retrieval replay never noticed, because the replay does not run the
  installer, while one such document makes the whole library uninstallable in `PREPARATION_FAILED`.

  Because the gate is only correct while it tracks the app, tests now bind it to
  `LibraryValidator` directly: the column limits, the span and section-path bounds, and the absence
  of any column exemption are read from the Kotlin source and compared to the packaging contract, so
  the two can no longer drift silently.

- Record contract: a document body may no longer carry the Unicode replacement character. The
  shared record contract exempted `body` from that rule while `LibraryValidator` on Android
  forbids U+FFFD in every `documents` column, so libraries built by the sanctioned pipeline were
  uninstallable by design - one offending guide failed the whole archive with `PREPARATION_FAILED`
  after its rights review had already accepted the character as an inherited upstream limitation.

  Extraction now routes those records into its existing rejected stream with a reason, which is
  counted in the shard manifest. Re-extracting the English Wikivoyage edition produced 34,597
  articles from the same pinned 2026-09 archive - two guides dropped for upstream corruption and
  one support asset curated out - and the resulting package installs and answers offline on the
  test device, which the 2026-09.4 edition could not do.

  Every admitted edition needs the same rebuild before publication; only `nairobi-health-places`
  was clean when all nine were audited. All nine were then rebuilt or re-admitted from a clean
  edition on 2026-10-24 and now pass the contract, so the catalog this release ships contains no
  archive the installer would refuse.

- The research prompt is now a short, explicit writing contract instead of a list of prohibitions.
  An answer opens with two or three sentences that give the result, continues in plain prose (a flat
  list only when the answer is steps, parts, or options, and no markdown headings or tables, because
  the chat surface renders plain text), cites each factual sentence in place as [S1], [S2], or [S3],
  and appends no sources list, because the app already shows the passages. The answer states the fact
  instead of narrating what a passage says — that narration went from present to absent in every
  measured arm. Where the local library cannot support a claim, the answer says it is not covered
  rather than passing memory as sourced fact. Hedging, moralizing, emojis, and a closing question are
  dropped; the app supplies the next-question offers.

  The prompt is also smaller: 910 characters, roughly a quarter fewer than the first
  draft of this contract, so the first turn of a conversation spends fewer tokens on
  instructions and more room stays available for passages.

  The prompt shrank as it got clearer: the pinned model's citation rate did not rise from it
  (measured 0 of 16 prose sentences with rules only, 1 of 6 with a domain-neutral worked example,
  after an apparent 8 of 11 turned out to be a contaminated example, and 3 of 7 for this simplified
  contract), so this release note claims no citation win. Response document shape still comes from
  the sampler grammar and the Kotlin re-check, never from prose; citation coverage stays a measured
  disclosure. A test refuses a system prompt that outgrows 1,400 characters.

- A research turn keeps its subject and its bound. Follow-up offers are now whole
  questions that name the article they came from, built only from verbatim library
  structure, and an offer the conversation already asked is not offered again. Bare
  fragments were the drift mechanism: a recorded journey went from getting around
  Kyoto to a national lodging essay to a town in Wyoming, one tap at a time.

  Retained history carries the last two earlier questions and no longer carries
  answers, because an earlier answer is this model's own prose and one turn replayed
  the previous turn word for word, markers and all, when it was handed back. The
  request is bounded at 6,000 characters with excerpts sized to what is left, and a
  turn that received no passage cannot claim `sourced` or `partial` and cannot write
  a marker at all: those alternatives leave the grammar, so an impossible claim is
  unrepresentable rather than something the finalizer must catch.

  Sampling order follows llama.cpp's own rejection path — prune, sample, verify one
  candidate against the grammar, and walk the vocabulary only to replace a rejected
  token — which removes the per-token mask cost the constrained path used to pay on
  this device. Measured on the acceptance emulator only: a real three-passage prompt
  runs 63.1 ms per token constrained against 63.4 unconstrained, and a four-turn
  journey takes 11.3-15.7 s per turn with first visible text at 5.3-7.7 s. No
  phone-class device has run these numbers, so nothing here is a speed claim.

- d346cbd: Screens read as an app, not a manual. One rule now applies to every surface: a state line
  with a number, then one control, with the explanation behind a single disclosure primitive.

  Setup, import, privacy/help/about, and the turn outcome lose their paragraph blocks. Each
  opens on a measured state line — "42% · 120 MB of 300 MB", "4096-token context · 1.2 GB ·
  runs here" — and moves the reasoning into `DetailDisclosure`, which `TraceCollapsible` now
  delegates to so research steps, evidence, and grounding expand identically. Strings revealed
  this way are named `*_detail`.

  Grounding becomes one line ("Sourced · 3 cited") instead of a titled panel; its chain is
  unchanged and still contains only what the turn recorded. The empty chat, welcome, import
  review, and settings/data screens follow the same shape: a state line, the numbers, one
  action, and a disclosure where a paragraph used to sit. Copy is now budgeted by
  `CopyDensityTest`: no string over 25 words, headings at most 4 words, labels at most 3, and
  at most 8 visible words per control in every source file that renders a control.

## 0.3.14

### Patch Changes

- Establish the changelog baseline. Versions before this entry are documented in [GitHub Releases](https://github.com/nonomnonom/blobs/releases).
