# Grilling and the Glossary

How Tether keeps a single shared vocabulary between humans and AI agents, and how that vocabulary is grown and policed.

Two artifacts work together:

- The **glossary** — one file at [`docs/glossary.md`](../glossary.md) that holds every load-bearing term Tether uses across product, engineering, and platform layers.
- The **grill-with-docs skill** — a sub-agent at [`.claude/skills/grill-with-docs/`](../../.claude/skills/grill-with-docs/SKILL.md) that interrogates a draft (spec, ux brief, ADR, issue body, review prompt) against the glossary, flags every term that drifts from its definition, and updates the glossary inline when a genuinely new term appears.

The skill set is adapted from Matt Pocock's [`mattpocock/skills`](https://github.com/mattpocock/skills) repo; the rationale for adopting it lives in [adr-pocock-skills-adoption.md](adr/adr-pocock-skills-adoption.md).

## Why this exists

A cross-platform P2P file-transfer app produces a lot of near-synonyms: "device" vs "peer" vs "node", "pairing" vs "trust" vs "handshake", "discovery" vs "rendezvous" vs "announce". Without one shared definition file, every spec invents its own naming and every agent latches onto a different synonym. The artifact that pays the cost is the next reviewer — human or AI — who cannot tell whether two passages describe the same mechanism.

The glossary is the rule the grill enforces; the grill is the mechanism that keeps the rule alive in everything written.

## The glossary

Lives at [`docs/glossary.md`](../glossary.md), at the root of `docs/` rather than under `docs/engineering/`. Terms cross all three documentation layers (product framing, engineering rules, platform knowledge), so the glossary sits above the layer split — every layer links into it.

### Sections

The glossary has three sections, each holding terms native to one concern:

- **Product** — user-facing concepts: *transfer*, *pairing*, *device list*, *trusted device*, *hotspot transfer*. The vocabulary spec-writer and ux-expert use.
- **Technical** — engineering concepts: *discovery*, *rendezvous*, *composition root*, *container*, *source set*. The vocabulary the architect, coder, and reviewers use.
- **Platform mapping** — the canonical names of platforms and their Kotlin Multiplatform targets: *Android* → `androidTarget`, *Desktop* → `jvm("desktop")`, *iOS* → `iosArm64` / `iosSimulatorArm64`, *macOS* → `macosArm64`. Platform names and target names are not interchangeable: "JVM" covers both Android and Desktop, "Apple" covers both iOS and macOS, and a draft that uses one where the other is meant points at a different subset of code than its author intends.

Each entry is one line: term — one-sentence definition — optional `(see <link>)` to the living doc that owns the deeper rule. Definitions in present tense, no history.

### Adding and editing terms

The glossary is owned collectively but written through one mechanism: the grill skill. When the grill is invoked on a draft and finds a term that is either new or used with a meaning that contradicts the existing entry, it updates the glossary in the same pass and references the update in its grill report. No agent edits the glossary outside a grill pass — this keeps drift visible.

A term enters the glossary when it appears in two or more long-lived artifacts, or when two writers (human or agent) use it with conflicting meanings in the same artifact surface. One-off task-local terms stay out.

## The grill-with-docs skill

Located at [`.claude/skills/grill-with-docs/`](../../.claude/skills/grill-with-docs/SKILL.md). The skill reads a draft, checks every load-bearing noun against [`docs/glossary.md`](../glossary.md), and produces a report with:

- terms that match the glossary as-is (acknowledged, not flagged);
- terms that drift from their glossary definition (flagged with the canonical alternative);
- terms that are new and need a glossary entry (added inline in the same pass);
- terms that are ambiguous because the glossary itself is silent or contradictory (escalated to the caller for resolution).

The skill is read-mostly for the draft (it does not rewrite the caller's artifact) and write-only for the glossary (the only file the skill itself edits).

## Mount points

Six places in the workflow invoke the grill. Each invocation has one job:

1. **`spec-writer`** — runs the grill on every spec draft before handing back to the orchestrator. Catches new product terms before they fossilise across features.
2. **`/implement` Gate G1** — runs the grill on the chosen issue body and any linked spec so downstream agents share vocabulary from the first turn of the loop.
3. **`/document` Step 2** — runs the grill on each layer artifact (spec / ux brief / engineering doc / ADR) as it's drafted, catching cross-layer term collisions early.
4. **`github-issue-author`** — runs the grill on every issue body it composes, so issue text matches the canonical names of the components and platforms it references.
5. **`review-guides`** — runs the grill on every review prompt template, so reviewers ask about the right concepts under the right names.
6. **`architect`** — runs the grill on every ADR and living-doc draft before publishing. Catches terms that drift between sibling engineering docs and the ADR introducing a new mechanism.

A mount point is a contract: that agent invokes the grill, surfaces its report, and either acts on the flags or escalates them. Skipping the grill at a mount point is a process violation, not a style preference.

## ADR authorship: only the architect

ADRs are written exclusively by the [architect](../../.claude/agents/architect.md) sub-agent. Other agents — including grill-with-docs — may surface that an ADR is *needed* (a contested choice with rejected branches worth recording), but they do not write the ADR themselves.

The rule exists because an ADR carries three load-bearing properties at once: a design palette with at least two rejected options reasoned through; a parent living doc that the ADR references for "what is" while it carries the "why"; and append-only history discipline (amendments as dated sections, no rewrites). The architect's procedure (Steps 1-8) is built around producing all three; no other agent's procedure is. Letting other agents draft ADRs produces orphans, single-option ADRs, or rewrites — all three failure modes the [ADR conventions](adr/README.md) exist to prevent.

The grill skill enforces vocabulary inside an ADR draft; it does not author the ADR.
