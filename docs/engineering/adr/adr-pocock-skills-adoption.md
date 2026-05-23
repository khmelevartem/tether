# Vocabulary scaffolding — vendor Pocock's `grill-with-docs` over write-from-scratch

**Status:** Accepted — 2026-05-23
**Issue:** [#36](https://github.com/khmelevartem/tether/issues/36)

## Context

Tether's spec / ux-brief / ADR / issue / review-prompt artifacts are written by a mix of humans and sub-agents (`spec-writer`, `ux-expert`, `architect`, `github-issue-author`, `review-guides`). Each writer reaches for its own near-synonyms — *device* vs *peer* vs *node*, *Desktop* vs *JVM*, *pairing* vs *trust*. The cost is paid by the next reviewer who cannot tell whether two passages describe the same thing.

The fix needs two pieces: a single glossary file that all layers link into, and a mechanism that runs on every new draft and updates the glossary inline when a genuinely new term appears.

Matt Pocock's [`mattpocock/skills`](https://github.com/mattpocock/skills) repo ships exactly this pair as Claude sub-agents: `grill-with-docs` (the interrogator) and `handoff` (a passing-context-between-agents helper). The form is proven, MIT-licensed, and small enough to vendor without becoming a maintenance liability. Parent living doc: [grilling-and-glossary.md](../grilling-and-glossary.md).

## Decision drivers

| Driver | Why it matters for Tether |
|---|---|
| One glossary reachable from every layer | Product, engineering, and platform-mapping terms all collide in the same artifacts; splitting the glossary by layer recreates the drift the mechanism is meant to close. |
| Minimal vendoring surface | Anything copied from upstream becomes a maintenance liability proportional to its size; smaller is better. |
| Coverage of the actual writing surface | The mechanism is only as good as the number of mount points it reaches; a partial wiring leaves silent gaps. |
| No new workflow conventions | The grill must slot into existing agent procedures and the existing ADR shape, not require renaming docs or restructuring `.claude/`. |
| Reusable scaffolding for future Pocock skills | Tether's roadmap includes a second Pocock-family skill ([#233](https://github.com/khmelevartem/tether/issues/233)); the chosen form should make that follow-up a copy, not a rebuild. |

## Considered options

### Option 1 — Vendor Pocock's `grill-with-docs` + `handoff` flat into `.claude/skills/`

Copy the two skills from `mattpocock/skills` into `.claude/skills/grill-with-docs/` and `.claude/skills/handoff/` as-is, with MIT-attribution in each `SKILL.md` header. Seed `docs/glossary.md` manually. Wire six mount points (spec-writer, `/implement` G1, `/document` Step 2, github-issue-author, review-guides, architect). Adapt only the ADR-format references inside the grill prompt to point at Tether's [`adr/_template.md`](_template.md). Closes all five drivers; costs are bounded vendoring drift and one extra grill turn per artifact.

### Option 2 — Write a Tether-native grill skill + glossary from scratch

Design our own grill prompt and glossary structure based on what the team already knows. No external dependency and full freedom to shape the report format, at the cost of re-inventing a form Pocock's repo has already iterated and forfeiting the reusable scaffolding for the future `improve-codebase-architecture` skill ([#233](https://github.com/khmelevartem/tether/issues/233)) that builds on the same Pocock family.

### Option 3 — Land the partial scaffolding from closed PR #221

PR #221 wired vocabulary policing only into `github-issue-author` and shipped no central glossary or general grill mechanism. Adopting it as-is yields one mount point instead of six, no glossary file, and a coupling between vocabulary rules and the issue-authoring prompt that has to be unwound the moment a second mount point appears — turning into a re-write rather than an extension. The `domain.md` naming it used also conflicts with the cross-layer `docs/glossary.md` placement.

### Option 4 — Do nothing; rely on inline vocabulary instructions inside each writing agent's prompt

Leave each sub-agent (`spec-writer`, `ux-expert`, etc.) responsible for naming consistency through its own prompt. Does not address the root cause — humans and AI agents do not share a single ubiquitous-language file, and inline instructions cannot enforce cross-agent consistency.

### Option 5 — Run `setup-matt-pocock-skills` and accept the full skill bundle

Use Pocock's bootstrap script to pull the entire skill family. The bundle includes skills Tether has no use for today and conventions that conflict with our existing prompt layout; the cost of pruning is higher than copying the two we actually use.

## Decision

We vendor `grill-with-docs` and `handoff` flat into `.claude/skills/`, seed `docs/glossary.md` manually with the three sections (product / technical / platform-mapping), and wire the six mount points. The grill is the only writer to the glossary; the architect remains the only writer of ADRs. ADR-format references inside the grill prompt are adapted to point at Tether's [`_template.md`](_template.md) — our ADR shape stays as it is.

This makes Tether's vocabulary discipline a structural property of the workflow (six mount points, one glossary, one grill) rather than a prompt-level reminder repeated across agents.

## Costs accepted

- **Vendoring drift.** Pocock's upstream skills may evolve; we own the copy and decide when to re-sync. The skills are small enough (<200 lines each) that drift cost is bounded.
- **Six mount points to maintain.** Each agent's procedure now includes "invoke grill, surface report". Skipping it at any point is a process violation, raising the bar for new sub-agents.
- **One more required pass per artifact.** Every spec / ux brief / ADR / issue / review prompt runs an extra grill turn before publishing. The cost is one sub-agent invocation per artifact, paid in latency, not in human time.

## Consequences

- `docs/glossary.md` becomes a load-bearing artifact: any rename of a load-bearing term lands there first, then propagates.
- Existing long-lived artifacts are not retro-grilled in this pass; they migrate naturally as they're next edited.
- `.claude/skills/grill-with-docs/SKILL.md` and `.claude/skills/handoff/SKILL.md` carry MIT-attribution headers pointing at upstream.
- The six mount-point agents' prompts are updated as part of #36's implementation — outside the scope of this ADR.

## Revisit if

- **A second naming-consistency incident lands despite the grill being in place.** Re-evaluate whether the mount points cover the actual writing surface, or whether the grill's report format is being ignored.
- **Pocock's upstream skill changes in a way that obsoletes our adaptation** (e.g. the upstream gains the same `adr/_template.md` configurability we hand-patched). Re-sync rather than maintain a diverging fork.
- **A third Pocock skill becomes load-bearing for Tether** (`improve-codebase-architecture`, #233). At that point, re-evaluate whether the flat-vendor approach still beats running `setup-matt-pocock-skills`.

## References

- [`grilling-and-glossary.md`](../grilling-and-glossary.md) — parent living doc; describes the mechanism in present tense.
- [`mattpocock/skills`](https://github.com/mattpocock/skills) — upstream source for the vendored skills.
- [Issue #36](https://github.com/khmelevartem/tether/issues/36) — the framing problem and the user's converged answers on the seven decision points.
- [Issue #233](https://github.com/khmelevartem/tether/issues/233) — future `improve-codebase-architecture` adoption that shares this scaffolding.
