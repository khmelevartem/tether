---
name: grill-with-docs
description: Grilling session that challenges a draft (spec / ux brief / engineering doc / ADR / issue body / review prompt) against Tether's glossary, sharpens terminology, and updates the glossary inline as decisions crystallise. Use when an agent or human reaches a mount point listed in docs/engineering/grilling-and-glossary.md.
---

<!--
Adapted from Matt Pocock's `grill-with-docs` skill (https://github.com/mattpocock/skills, MIT-licensed, Copyright 2026 Matt Pocock).
Tether-specific changes: single context, glossary at `docs/glossary.md`, ADRs use `docs/engineering/adr/_template.md`, the skill does not author ADRs (architect agent does).
Mechanism overview: `docs/engineering/grilling-and-glossary.md`.
-->

<what-to-do>

Interview the caller relentlessly about the draft until you and the caller share a vocabulary. Walk down each branch of the design tree, resolving disagreements between the draft and `docs/glossary.md` one-by-one. For each question, give your recommended answer.

Ask the questions one at a time, waiting for feedback on each before continuing.

If a question can be answered by exploring the codebase or sibling docs, explore instead of asking.

</what-to-do>

<supporting-info>

## Domain awareness

Tether is a single-context repo. The glossary lives at one path: `docs/glossary.md`. There is no `CONTEXT-MAP.md` and no per-module glossary. If the glossary file does not yet exist, create it lazily — only when the first new term is resolved.

The glossary has three sections, in fixed order:

- **Product** — user-facing concepts (`transfer`, `pairing`, `device list`, `trusted device`, `hotspot transfer`).
- **Technical** — engineering concepts (`discovery`, `rendezvous`, `composition root`, `container`, `source set`).
- **Platform mapping** — canonical names for platforms and their Kotlin Multiplatform targets (`Android` → `androidTarget`, `Desktop` → `jvm("desktop")`, `iOS` → `iosArm64` / `iosSimulatorArm64`, `macOS` → `macosArm64`).

Each entry is one line: `**term** — one-sentence definition. _Avoid:_ near-synonyms.` Definitions are tight (1–2 sentences), in present tense, no history. See [grilling-and-glossary.md](../../../docs/engineering/grilling-and-glossary.md) for the full rule.

ADRs live at `docs/engineering/adr/adr-<name>.md` and use the Tether shape in [`docs/engineering/adr/_template.md`](../../../docs/engineering/adr/_template.md) — Context / Decision drivers / Considered options / Decision / Costs accepted / Consequences / Revisit if / References. Naming is by slug, not by sequential number.

## During the session

### Challenge against the glossary

When the draft uses a term that conflicts with `docs/glossary.md`, surface the conflict in one line: *"the glossary defines X as A, but here it's used as B — which is it?"* Do not silently rewrite the draft; the draft's author decides.

### Sharpen fuzzy language

When the draft uses an overloaded term, propose a precise canonical alternative. *"You wrote `account` — do you mean the **Trusted device** or the local **user profile**? Those are different things in this codebase."*

### Probe edge cases

When relationships between concepts are stated, invent a concrete scenario that forces the boundary to be precise. *"You wrote that a transfer pauses when the peer goes offline. What happens if the peer reappears on a different network — is it the same transfer resuming, or a new one?"*

### Cross-reference with code

When the draft states how something works, check whether the code agrees (via `Grep` / `Read`). If a contradiction shows up, surface it: *"the draft says pairing survives a reinstall, but the code keys pairing by `installId` which changes on reinstall — which one is right?"*

### Update the glossary inline

When a term is resolved, update `docs/glossary.md` in the same pass. Don't batch — capture it as it happens. Use the section that matches the term's layer (product / technical / platform-mapping). Format: one line, bold term name, one-sentence definition, `_Avoid:_` list when near-synonyms exist.

The glossary holds only terms specific to Tether. General programming concepts (timeouts, error types, coroutines) don't belong, even when the project uses them constantly. Before adding: is this concept unique to Tether's domain, or general?

The glossary is the **only** file this skill edits. Drafts are read, not rewritten — the draft's owning agent (or human) acts on the grill report.

## ADR candidates — flag, don't write

When a decision surfaces during the grill that meets all three of these:

1. **Hard to reverse** — changing your mind later costs real work.
2. **Surprising without context** — a future reader will wonder why.
3. **The result of a real trade-off** — there were genuine alternatives and one was picked for specific reasons.

…then flag it as an ADR-candidate in your grill report. Do not write the ADR yourself. The [`architect`](../../agents/architect.md) sub-agent is the only writer of ADRs in this repo — the rule is in [`grilling-and-glossary.md`](../../../docs/engineering/grilling-and-glossary.md) §ADR authorship. The architect's procedure ensures the parent living doc exists, the design palette has ≥2 rejected alternatives reasoned through, and amendments stay append-only — properties this skill's flow does not guarantee.

If any of the three triggers above is missing, skip the ADR. Most decisions don't need one.

## Output to caller

A short report with these sections, in order:

- **Glossary matches** — terms used as-is per `docs/glossary.md`. Acknowledged, not flagged.
- **Drift flags** — terms used with a meaning that contradicts the glossary; each line names the canonical alternative.
- **Glossary additions** — new entries written to `docs/glossary.md` in this pass; one line per term.
- **Ambiguities** — terms the glossary is silent or contradictory on; escalated to the caller for resolution.
- **ADR candidates** — decisions that meet all three triggers above; handed to the orchestrator for `architect` dispatch.

</supporting-info>
