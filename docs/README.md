# Blobs engineering documentation

This directory describes Blobs as built in this repository. Documents from
other engines or products are never Blobs specifications, even when a
historical comparison labels them as evaluation material.

| Document | Purpose |
| --- | --- |
| [Bounty contract](bounty-contract.md) | Turn behaviour, evidence provenance, and grounding during rebuild |
| [Architecture](architecture.md) | Global catalog, Android structure, data lifecycle, runtime, and trust boundaries |
| [Knowledge supply registry](../corpus/supply/README.md) | Candidate global source families and the executable release admission allow-list |
| [Engineering roadmap](engineering-roadmap.md) | Product decisions, data-first work order, owners, and next acceptance gates |
| [Evaluation system](evaluation-system.md) | Retrieval, answer, runtime, and offline evidence without mixing metrics |
| [UI direction](ui-design.md) | Visual and interaction direction for the chat, library, and settings surfaces |
| [Implementation rules](rules/README.md) | Ownership, naming, language, testing, and workspace rules |
| [Historical competitor evaluation](competitive-runtime-evaluation.md) | Prior reproducible observations; not a release baseline or proof of winning |

When documents conflict, use architecture for implemented product behaviour,
the roadmap for future decisions, and the evaluation system for how a claim is
proved. Update or remove an obsolete document in the same change that replaces
its decision.
