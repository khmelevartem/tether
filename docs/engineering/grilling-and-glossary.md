# Glossary discipline

How Tether keeps a single shared vocabulary between humans and AI agents.

Two artifacts work together:

- The **glossary** — one file at [`docs/glossary.md`](../glossary.md) that holds every load-bearing term Tether uses across product, engineering, and platform layers.
- The **`review-glossary` agent** at [`.claude/agents/review-glossary.md`](../../.claude/agents/review-glossary.md) — a sub-agent that samples load-bearing nouns in a diff against the glossary, flags drift, and flags terms used without an entry. It does not edit the glossary itself.

## Why this exists

A cross-platform P2P file-transfer app produces a lot of near-synonyms: "device" vs "peer" vs "node", "pairing" vs "trust" vs "handshake", "discovery" vs "rendezvous" vs "announce". Without one shared definition file, every spec invents its own naming and every agent latches onto a different synonym. The artifact that pays the cost is the next reviewer — human or AI — who cannot tell whether two passages describe the same mechanism.

The glossary is the rule `review-glossary` enforces on every PR diff.

## The glossary

Lives at [`docs/glossary.md`](../glossary.md). Terms cross all three documentation layers: product framing, engineering rules, platform knowledge.

Each entry is one line: bold term — one-sentence definition — optional `_Avoid:_` near-synonyms — optional `(see <link>)` to the living doc that owns the deeper rule. Definitions in present tense, no history.

## How drift is caught

`review-glossary` runs in every PR review wave:

- `/implement` Step 4 (inner-loop reviewer wave) and Step 6 (full pre-PR review wave A);
- `/document` Step 5 (review wave);
- `github-issue-author` Step 4 (before showing the draft to the user).

The agent samples load-bearing nouns in the prose surfaces of the diff (KDoc, docstrings, comments, every touched file under `docs/` and `.claude/`), compares against the glossary, and emits `[REQUIRED]` findings for drift and for new domain terms without an entry.

## Adding and editing terms

A glossary entry is added by the writing agent (`spec-writer` / `ux-expert` / `architect`, or the orchestrator for inline edits) as part of addressing a `[REQUIRED]` finding from `review-glossary`. The agent picks the section (Product / Technical), writes one line in the canonical shape (bold term — definition — optional `_Avoid:_` — optional `(see <link>)`), and re-runs the review wave; the next pass verifies the entry shape.

Pruning accidental additions is `review-glossary`'s role on later PRs — an entry whose term does not recur across the codebase is `[REQUIRED]` for removal.

## ADR authorship: only the architect

ADRs are written exclusively by the [architect](../../.claude/agents/architect.md) sub-agent. Other agents may surface that an ADR is *needed*, but they do not write the ADR themselves.

`review-glossary` enforces vocabulary inside an ADR draft; it does not author the ADR.
