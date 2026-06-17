# Glossary discipline

How Tether keeps a single shared vocabulary between humans and AI agents.

Two artifacts work together:

- The **glossary** — one file at [`docs/glossary.md`](../glossary.md) that holds every load-bearing term Tether uses across product, engineering, and platform layers.
- The **`review-glossary` agent** at [`.claude/agents/review-glossary.md`](../../.claude/agents/review-glossary.md) — a sub-agent that samples load-bearing nouns in a diff against the glossary, flags drift, and flags terms used without an entry. It does not edit the glossary itself.

## The glossary

Lives at [`docs/glossary.md`](../glossary.md). Terms cross all three documentation layers: product framing, engineering rules, platform knowledge. Entry shape is declared in the glossary's own header — defer there, do not restate.

## What qualifies as a term

A term must **carry meaning outside the code** — something product or engineering discussions reference by name. It recurs across long-lived artifacts (specs, engineering docs, ADRs, review comments) as a concept, not as a symbol reference.

What does NOT qualify, even if it recurs in the diff:
- Kotlin type names.
- Function / method names.
- API / library symbol names.
- Implementation techniques tied to a specific call site — describe them where the technique lives, not in the glossary.

A symbol from the code that also names a stable engineering concept (the server, the storage, a view, a test fixture) does qualify — the entry is about the concept, the symbol name happens to match.

## How drift is caught

`review-glossary` runs in every PR review wave:

- `/implement` Step 5 (inner-loop reviewer wave) and Step 8 (full pre-PR review wave A);
- `create-issue` Step 4 (before showing the draft to the user).

The agent samples load-bearing nouns in the prose surfaces of the diff (KDoc, docstrings, comments, every touched file under `docs/` and `.claude/`), compares against the glossary, and emits `[REQUIRED]` findings for drift and for new domain terms without an entry.

## Adding and editing terms

A glossary entry is added by the writing agent (`spec-writer` / `ux-expert` / `architect`, or the orchestrator for inline edits) as part of addressing a `[REQUIRED]` finding from `review-glossary`. The agent picks the section (Product / Technical), writes one line in the shape declared by the glossary header, and re-runs the review wave; the next pass verifies the entry shape.

Pruning accidental additions is `review-glossary`'s role on later PRs — an entry whose term does not recur across the codebase is `[REQUIRED]` for removal.

