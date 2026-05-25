# Architecture Decision Records

ADRs in this folder record one-time architectural choices: context, options considered, decision, and consequences. Each ADR is append-only history. Current state and conventions live in the parent [`docs/engineering/`](..) folder (architecture-principles, dependency-injection, modules, presentation-layer, ...).

Operational artefacts — agent definitions in `.claude/agents/` and skills in `.claude/skills/` — reference the living docs in `docs/engineering/`, not the ADRs. The ADR carries the *why* for human readers; the living doc carries the *what is currently true* that agents enforce.

Start a new ADR from [`_template.md`](_template.md) — it captures the canonical shape used across the existing ADRs (Context / Decision drivers / Considered options / Decision / Costs accepted / Consequences / Revisit if / References) with one-line guidance per section. Optional sections (Decision drivers, Comparison, Revisit if, References) are marked — drop them when the decision is narrow. For broader background on the format, see [adr.github.io](https://adr.github.io/) and [Joel Parker Henderson's collection](https://github.com/joelparkerhenderson/architecture-decision-record).

Write an ADR when a non-trivial choice will be questioned by future contributors (library, framework, structural pattern). Skip it for naming or style — those belong in living docs.

## What the Decision section must (not) say

The Decision section names the *choice*, not the *state*. Drift-prone formulations:

- ❌ «`FileServer.jvm` uses Ktor CIO with `sslConnector`». Operational claim about a concrete module — will become stale the moment that module changes.
- ❌ «After this lands, `FileClient` exposes `send(Path)`». Snapshot of an API surface — code drifts faster than the ADR can be updated.
- ✅ «We choose Ktor CIO for the JVM server engine because…». States the choice and the rationale; future state lives in the parent living doc.

If a Decision needs to describe operational reality — what *currently exists in code* — that belongs in the corresponding parent living doc under `docs/engineering/` (e.g. [`dependency-injection.md`](../dependency-injection.md), [`modules.md`](../modules.md)). The ADR references the living doc for "what is"; the living doc references the ADR for "why".

**Before writing an ADR, check:** does a parent living doc for this subsystem exist? If not, create or extend one in the same PR. Skipping this step is the failure mode that turns ADRs into pseudo-living-docs that silently drift.

## Reversing an ADR

A decision occasionally needs reversal (Revisit-if trigger fired, upstream constraint changed). Do not rewrite the body — the original context is what makes the reversal legible.

Add at the top, right after the `**Issue:**` line:

```
**Status:** Reversed — YYYY-MM-DD. Originally accepted YYYY-MM-DD.

**Reversal (YYYY-MM-DD).** One paragraph naming the new active position and the evidence that fired the trigger — link to GH issues, upstream tickets, commits as needed. Include the re-revisit trigger for the reversal itself.
```

Move the original body under `## Original decision (superseded)` with a one-line marker: «kept as-is for context — do not edit, the active position is the reversal above».

For each ADR whose body references the now-removed thing, add one line in its header: `**Note (YYYY-MM-DD):** <X> removed — see [adr-foo.md](adr-foo.md) §Reversal.`. Do not edit those ADR bodies either — the header link is enough for a reader to land on the active position.
