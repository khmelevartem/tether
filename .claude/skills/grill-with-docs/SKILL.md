---
name: grill-with-docs
description: Grilling session that challenges a draft (spec / ux brief / engineering doc / ADR / issue body / review prompt) against Tether's glossary, sharpens terminology, and updates the glossary inline as decisions crystallise. Invoked by any agent whose output is a long-lived prose artifact, per docs/engineering/grilling-and-glossary.md.
---

<!--
Adapted from Matt Pocock's `grill-with-docs` skill (https://github.com/mattpocock/skills, MIT-licensed). Full upstream license text: ../THIRD_PARTY_LICENSES.md.
Tether-specific changes: single context, glossary at `docs/glossary.md`, ADR authorship handled by the architect agent (not this skill).
Mechanism overview: `docs/engineering/grilling-and-glossary.md`.
-->

<what-to-do>

Interview the caller relentlessly about the draft until you and the caller share a vocabulary. Walk down each branch of the design tree, resolving disagreements between the draft and `docs/glossary.md` one-by-one. For each question, give your recommended answer.

Ask the questions one at a time, waiting for feedback on each before continuing.

If a question can be answered by exploring the codebase or sibling docs, explore instead of asking.

</what-to-do>

<supporting-info>

## Domain awareness

Tether is a single-context repo. The glossary lives at ONLY one path: `docs/glossary.md`. If the glossary file does not yet exist, create it lazily — only when the first new term is resolved.

Each entry is one line: `**term** — one-sentence definition. _Avoid:_ near-synonyms.` Definitions are tight (1–2 sentences), in present tense, no history. See [grilling-and-glossary.md](../../../docs/engineering/grilling-and-glossary.md) for the full rule.

## During the session

### Challenge against the glossary

When the draft uses a term that conflicts with `docs/glossary.md`, surface the conflict in one line: *"the glossary defines X as A, but here it's used as B — which is it?"* Do not silently rewrite the draft; the draft's author decides.

### Sharpen fuzzy language

When the draft uses an overloaded term, propose a precise canonical alternative. *"You wrote `account` — do you mean the **Trusted device** or the local **user profile**? Those are different things in this codebase."*

### Probe edge cases

When relationships between concepts are stated, invent a concrete scenario that forces the boundary to be precise. *"You wrote that a transfer pauses when the peer goes offline. What happens if the peer reappears on a different network — is it the same transfer resuming, or a new one?"*

### Update the glossary inline

When a term is resolved, update `docs/glossary.md` in the same pass. Don't batch — capture it as it happens. Use the section that matches the term's layer (product / technical). Format: one line, bold term name, one-sentence definition, `_Avoid:_` list when near-synonyms exist.

The glossary holds only terms specific to Tether. General programming concepts (timeouts, error types, coroutines) don't belong, even when the project uses them constantly. Before adding: is this concept unique to Tether's domain, or general?

The glossary is the **only** file this skill edits. Drafts are read, not rewritten — the draft's owning agent (or human) acts on the grill report.

## Output to caller

A short report with these sections, in order:

- **Glossary matches** — terms used as-is per `docs/glossary.md`. Acknowledged, not flagged.
- **Drift flags** — terms used with a meaning that contradicts the glossary; each line names the canonical alternative.
- **Glossary additions** — new entries written to `docs/glossary.md` in this pass; one line per term.
- **Ambiguities** — terms the glossary is silent or contradictory on; escalated to the caller for resolution.
- **ADR candidates** — decisions that surfaced during the grill and look ADR-worthy; handed to the orchestrator for `architect` dispatch (the architect decides whether the decision actually warrants an ADR, per its own procedure).

</supporting-info>
