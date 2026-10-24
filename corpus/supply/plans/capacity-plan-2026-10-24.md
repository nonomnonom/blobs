# Capacity plan: big English reference and history lane

**Superseded for the big reference. The owner cancelled the Top 1M full-text lane
on 2026-10-24 because the 17,134,155,148-byte archive and its five-volume payload
are too large for the product. The reference sections below are decision evidence
only: do not acquire the archive or execute the staging gate. The history-lane
retrospective and the reclamation inventory remain current.**

**Measured 2026-10-24. This is a staging and aggregate-storage plan, not a rights
review, release admission, or acquisition result.** All sizes below come from
receipts and reviewed manifests already on this machine, except the
Kiwix-declared size and digest of the not-yet-downloaded Top 1M full-text
archive, which are labelled as publisher-declared.

## Why this plan exists

The strategy once asked for the Top 1M full text as deterministic complementary
volumes and for a history/primary-texts lane. The owner cancelled the reference
lane on 2026-10-24; the history lane is built and this document records what it
actually cost. The reference measurements remain as the evidence behind the
cancellation.

## Measured basis

Host free space at measurement: **35,121,360,896 bytes (32.71 GiB)** on `D:`.

| Evidence | Measured value |
| --- | ---: |
| `wikipedia_en_top1m_mini_2026-04.zim` source | 3,022,411,034 bytes |
| Top 1M mini reviewed text (1,187 shards, 997,717 docs) | 1,524,851,798 UTF-8 bytes |
| Top 1M mini reviewed records directory | 815 MiB |
| Top 1M mini payload / archive | 4,979,904,512 / 1,787,166,230 bytes |
| `wikisource_en_all_nopic_2026-09.zim` source | 3,905,588,830 bytes |
| Wikisource reviewed text (174 shards, 622,149 docs) | 6,433,764,727 UTF-8 bytes |
| Wikisource reviewed records directory | 2.6 GiB |
| Wikisource four payloads | 12,543,102,976 bytes |
| Wikisource four archives | 5,311,424,743 bytes |
| Admitted portfolio payload, including the Nairobi places shard | 20,248,195,072 bytes |

Measured scaling factors:

| Ratio | Top 1M mini | Wikisource |
| --- | ---: | ---: |
| reviewed records / source archive | 0.27 | 0.67 |
| normalized text / source archive | 0.50 | 1.65 |
| installed payload / normalized text | 3.27 | 1.95 |
| installed payload / `.blobs` archive | 2.79 | 2.36 |

Use the range, never one point: records are 0.27–0.67 of the source, payload is
1.95–3.27 times normalized text, and payload is 2.36–2.79 times the archive.
The Top 1M mini sits at the low records factor because its articles are lead
sections; full text should sit nearer Wikisource, so the high end is the safe
planning case.

## History and primary texts: measured result

The lane is complete and admitted as four volumes of
`wikisource-en-all-nopic` `2026-09.2`. Its forward capacity is zero: all four
payloads and archives exist and are retained as admitted editions.

| Volume | Documents | Passages | Payload bytes | Archive bytes |
| --- | ---: | ---: | ---: | ---: |
| p000 | 155,569 | 1,432,332 | 3,131,916,288 | 1,326,111,432 |
| p001 | 155,765 | 1,427,120 | 3,125,862,400 | 1,323,811,991 |
| p002 | 155,410 | 1,440,217 | 3,154,440,192 | 1,336,109,643 |
| p003 | 155,405 | 1,433,801 | 3,130,884,096 | 1,325,391,677 |
| total | 622,149 | 5,733,470 | 12,543,102,976 | 5,311,424,743 |

Peak staging actually held while building all four at once: source 3.64 GiB +
extract shards 2.51 GiB + reviewed copy 2.42 GiB + four payloads 11.68 GiB +
four archives 4.95 GiB = **25.20 GiB**. The pre-build estimate was 24.5 GB; the
difference is +2.4%, so the estimate method is sound. No further staging is
required, and the lane consumed 12.54 GB of the 48 GB aggregate payload
safeguard.

## Big reference: the edition to acquire

The registry currently names `wikipedia_en_top1m_nopic_2026-05.zim`. That file
does not exist on the Kiwix mirror. The real full-text edition next to the
admitted mini is:

| Field | Value |
| --- | --- |
| File | `wikipedia_en_top1m_nopic_2026-04.zim` |
| URL | `https://download.kiwix.org/zim/wikipedia/wikipedia_en_top1m_nopic_2026-04.zim` |
| Size | 17,134,155,148 bytes (15.96 GiB), publisher-declared and matched by HTTP `Content-Length` |
| SHA-256 | `f0ad000be866fa576448a861e42cbaecd87df0d376c1f97f36e12aef58082662` |
| Checksum source | `https://download.kiwix.org/zim/wikipedia/wikipedia_en_top1m_nopic_2026-04.zim.meta4` |
| Publisher | Kiwix project; Wikipedia contributors |
| Local verification | **none yet** — do not write a source lock until the downloaded file matches this size and digest |

The separately locked `wikipedia_en_all_nopic_2026-06.zim` is 52,690,706,555
bytes and 18 volumes at the measured factors. It stays out of scope on this
machine.

## Volume count is capped by the aggregate safeguard, not by 6 GiB alone

Per-library limit: 6,442,450,944 bytes payload.

The 48,000,000,000-byte aggregate safeguard is evaluated against installed
payloads. The full-text family replaces the mini, so the baseline is:

```
20,248,195,072 admitted now
- 4,979,904,512 retire wikipedia-top1m-mini when volume 1 is admitted
= 15,268,290,560 baseline
```

That leaves **32,731,709,440 bytes (30.48 GiB)** for the whole full-text set.
Five volumes at the 6 GiB limit total 32,212,254,720 bytes and fit. Six volumes
at 6 GiB total 38,654,705,664 bytes and exceed the safeguard by 5.92 GB.
Therefore the deterministic set is **five volumes**, not the "at least six"
written in the registry. If extraction proves that five full volumes are not
enough for an honest slice, reduce the article selection or retire another
admitted library; do not add a sixth volume.

Five partitions of `sha256-document-id-modulo-v1` over the complete reviewed
manifest, each below 6,442,450,944 bytes, with the sum asserted at
≤32,731,709,440 bytes before packaging.

## Staging math

Notation: `S` source 15.96 GiB, `R` reviewed records (0.27–0.67 × `S` =
4.31–10.69 GiB), `P` one 6.0 GiB payload, `A` one archive at 2.4–2.8 × ratio
(≈2.3–2.6 GiB), `M` 1 GiB free margin.

Packaging all five payloads before publishing, as the strategy's first estimate
assumed, needs `S + 2R + 5P + 5A + M` = **79–86 GiB**. That is why the plan
packages one volume at a time.

| Phase | Peak requirement | Verdict at 32.71 GiB free |
| --- | --- | --- |
| Download only | `S + M` = 16.96 GiB | fits; leaves 15.75 GiB |
| Extract + curate | `S + 2R + M` = 25.6 GiB (low), 31.0 GiB (mid), 38.3 GiB (high) | fits only if `R ≤ 7.85 GiB` (≤0.49 × `S`); the yield is unknown until extraction |
| Package sequentially, archives kept on `D:` | `S + R + P + 5A + M` = 40.5 GiB (mid) | does not fit |
| Package sequentially, archives moved off `D:` | `S + R + P + M` = 30.5 GiB (mid), 33.6 GiB (high) | fits at mid only |

Conclusion: **the current 32.71 GiB free is not a safe single-disk budget for
this lane.** The high-yield case fails the curate phase, and retaining finished
archives fails the package phase.

### Option A — second staging volume (preferred)

Put the download, extract shards, reviewed copy, payloads, and finished archives
on a second volume with **at least 60 GiB free**. `D:` then needs only 17 GiB
during the run and keeps the final archives afterwards. This preserves every
admitted edition and needs no deletion of evidence.

### Option B — single disk after measured reclamation

Reclaim before starting and move each finished archive off `D:` immediately.
Required free space: **≥34 GiB** for the curate phase at the high records
factor, or **≥32 GiB** at the midpoint, plus the archive destination. Current
free is 32.71 GiB, so this option needs reclamation anyway.

Measured reclaim candidates (gross 23.4 GiB; some entries may share hardlinks
and free less):

| Candidate | Size | Condition before removal |
| --- | ---: | --- |
| `corpus/work/diag-fresh.sqlite` | 2.93 GiB | diagnostic, rebuildable from records |
| `corpus/work/diag-p000.sqlite` | 2.93 GiB | diagnostic, rebuildable from records |
| `corpus/work/eval-top1m-v2/probe.sqlite` | 4.64 GiB | evaluation probe; keep the result report |
| `corpus/work/extract-v3` + `corpus/work/extract` | 3.42 GiB | confirm no hardlinks into retained reviewed shards first |
| `corpus/work/retired` | 4.00 GiB | superseded packages; locks, receipts, and manifests stay |
| duplicate `diy.stackexchange.com_en_all_2026-10.zim` | 1.94 GiB | byte-identical SHA-256 to the retained copy; keep one |
| five superseded Wikivoyage archives | 1.71 GiB | keep only the admitted `2026-09.4` archive |
| `wikipedia-top1m-mini-2026-04.3.blobs` | 1.66 GiB | superseded by the admitted `.4` archive |
| `probe-stackexchange-cooking.blobs` | 0.14 GiB | probe only |

Never remove an admitted payload or archive, an admitted lane's source ZIM, a
reviewed manifest, a receipt, or a source lock to make room.

## Preflight gates

1. Pin the corrected edition and digest in a source lock; mark it verified only
   after the local file matches 17,134,155,148 bytes and
   `f0ad000be866fa576448a861e42cbaecd87df0d376c1f97f36e12aef58082662`.
2. Correct the registry: origin `2026-04`, five volumes, aggregate cap
   32,731,709,440 bytes, and a pointer to this plan.
3. Confirm the staging destination and free-space gate; record the reading.
4. Download to the staging volume and verify size and SHA-256 before extraction.
5. Extract; record actual text bytes and `R`; recompute the partition plan; fail
   the build if any partition exceeds 6,442,450,944 bytes or the sum exceeds
   32,731,709,440 bytes.
6. Curate; assert the exclusion report; run `portfolio.assert_distinct` against
   every retained records set. Overlap with the mini is expected and is resolved
   by retiring the mini at the first full-text admission, not before.
7. Package one volume at a time; verify each receipt; move each archive off the
   staging disk before building the next payload.
8. Author per-volume retrieval cases including cross-volume queries before any
   volume enters `release_ready`; keep host replay separate from device
   acceptance.
9. Retire `wikipedia-top1m-mini` in the same registry change that admits volume
   one, so the portfolio never advertises two English Wikipedia installs.
10. Audit aggregate payload against 48,000,000,000 bytes after each admission.

## Known limitation this plan does not fix

The admitted mini replays 7 of 12 expected titles (58.3%), with every found
title at rank 1. Full text adds content but does not by itself fix candidate
depth or ranking. Treat the reference lane as dependably usable only after the
five-volume slice passes cross-volume cases and a phone run; do not infer that
from this capacity plan.
