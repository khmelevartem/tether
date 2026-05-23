# Grilling and the Glossary

How Tether keeps a single shared vocabulary between humans and AI agents, and how that vocabulary is grown and policed.

Two artifacts work together:

- The **glossary** — one file at [`docs/glossary.md`](../glossary.md) that holds every load-bearing term Tether uses across product, engineering, and platform layers.
- The **grill-with-docs skill** — a sub-agent at [`.claude/skills/grill-with-docs/`](../../.claude/skills/grill-with-docs/SKILL.md) that interrogates a draft (spec, ux brief, ADR, issue body, review prompt) against the glossary, flags every term that drifts from its definition, and updates the glossary inline when a genuinely new term appears.

## Why this exists

A cross-platform P2P file-transfer app produces a lot of near-synonyms: "device" vs "peer" vs "node", "pairing" vs "trust" vs "handshake", "discovery" vs "rendezvous" vs "announce". Without one shared definition file, every spec invents its own naming and every agent latches onto a different synonym. The artifact that pays the cost is the next reviewer — human or AI — who cannot tell whether two passages describe the same mechanism.

The glossary is the rule the grill enforces; the grill is the mechanism that keeps the rule alive in everything written.

## The glossary

Lives at [`docs/glossary.md`](../glossary.md). Terms cross all three documentation layers: product framing, engineering rules, platform knowledge.

Each entry is one line: bold term — one-sentence definition — optional `_Avoid:_` near-synonyms — optional `(see <link>)` to the living doc that owns the deeper rule. Definitions in present tense, no history.

## Adding and editing terms

The glossary is owned collectively but written through one mechanism: the grill skill. When the grill is invoked on a draft and finds a term that is either new or used with a meaning that contradicts the existing entry, it updates the glossary in the same pass and references the update in its grill report. No agent edits the glossary outside a grill pass — this keeps drift visible.

The grill writes a term on first sighting if it judges the term as Tether-domain-specific (the «is this unique to Tether or general programming?» filter inside the skill). One-off task-local terms stay out by that judgement, not by waiting for a second artifact to confirm. Pruning of accidental additions is `review-guides`' role on later PRs.

## Who invokes the grill

Every agent whose output is a long-lived prose artifact (spec, UX brief, engineering doc, ADR, knowledge entry, issue body, review prompt, `.claude/` skill or agent prompt) invokes the grill on its draft before returning. Each agent's own definition encodes the call; the orchestrators (`/implement`, `/document`) mirror the discipline with one vocabulary-pass paragraph each.

The grill is not optional at these surfaces — skipping it is a process violation, not a style preference. Conformance is verified by `review-guides`, which runs the grill on the prose parts of every PR diff as the long-tail safety net.

## ADR authorship: only the architect

ADRs are written exclusively by the [architect](../../.claude/agents/architect.md) sub-agent. Other agents — including grill-with-docs — may surface that an ADR is *needed*, but they do not write the ADR themselves.

An ADR carries three load-bearing properties at once: a design palette with at least two rejected options reasoned through; a parent living doc that the ADR references for "what is" while it carries the "why"; and append-only history discipline (amendments as dated sections, no rewrites). The architect's procedure is built around producing all three; no other agent's procedure is.

The grill enforces vocabulary inside an ADR draft; it does not author the ADR.
