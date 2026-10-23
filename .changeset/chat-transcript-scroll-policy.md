---
"blobs": minor
---

Chat transcript: the reader owns the scroll. A new turn anchors near the top with a peek of
the previous turn, a streaming answer is followed only while the reader is already at the
live edge, and a saved thread reopens on its last anchored turn instead of the raw bottom.

Turn states now share one step shape (`Trace`, `TraceLine`, `TraceCollapsible`): the live
research stage, a finished turn's measured passages, citations, and elapsed time, the
evidence list, and grounding. Grounding changes from a single caption into an expandable
"How this answer was checked" chain whose every line is a fact this turn recorded — scope
searched, passages found, time taken, passages carrying a marker, the model's own support
claim, and the structural verdict. Blobs runs one local completion, so the chain shows no
model reasoning and invents none.
