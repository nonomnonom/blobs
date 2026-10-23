---
"blobs": minor
---

Screens read as an app, not a manual. One rule now applies to every surface: a state line
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
