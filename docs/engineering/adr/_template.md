# <Choice in one line — names the *what*, not the *how>

<!-- Title examples that work:
       "Network stack — Ktor CIO (client + server), tactically not fundamentally"
       "macOS target — Native over Desktop JVM"
     Title examples that don't:
       "Network stack decision"          (says nothing)
       "FileServer.jvm uses sslConnector" (operational state, not a choice — see adr/README.md)
-->

**Status:** Accepted — YYYY-MM-DD
**Issue:** [#N](https://github.com/khmelevartem/tether/issues/N)

## Context

<!-- One or two paragraphs. What problem forces this decision now? What constraints from the surrounding system (existing living docs, platform reality, product invariants) bound the option space? Link the parent living doc — every ADR must reference one (see adr/README.md). -->

## Decision drivers

<!-- Optional. Use when the option space is wide and the picks depend on multiple weighted criteria. A small table works well; keep one row per driver, one sentence per row. Drop the section entirely for narrow decisions. -->

| Driver | Why it matters for Tether |
|---|---|
| ... | ... |

## Considered options

<!-- One section per architecturally distinct option. Include the chosen one and at least two rejected ones. For each: one paragraph on the mechanism, one or two lines on what it closes and what it costs. Heavy comparison goes in the optional Comparison table below; this section stays narrative. -->

### Option 1 — <name>
<!-- mechanism / what it closes / what it costs -->

### Option 2 — <name>
<!-- ... -->

### Option 3 — <name>
<!-- ... -->

## Comparison

<!-- Optional. A table comparing options against drivers, when the differences are hard to hold in the head. Skip for 2-3 options or when the trade-offs are obvious from the per-option paragraphs. -->

| | Option 1 | Option 2 | Option 3 |
|---|---|---|---|
| <driver> | ... | ... | ... |

## Decision

<!-- The *choice*, not the *state*. Per adr/README.md: name what we pick and why, in present tense. Avoid operational claims about concrete modules — those drift; they live in the parent living doc, which this ADR references for "what is" while this ADR carries the "why".

  ✅ "We choose Ktor CIO for the JVM server engine because ..."
  ❌ "`FileServer.jvm` uses Ktor CIO with `sslConnector`."
-->

## Costs accepted

<!-- The trade-offs we knowingly take. Each one a sentence or short paragraph. Use when there are concrete downsides the decision swallows (typical case for non-trivial ADRs). Merge into Consequences if the list is short. -->

## Consequences

<!-- What changes as a result, what becomes harder or easier, what follow-ups are implied. Status of currently open issues in the area, if relevant. For amendments to earlier ADRs, add a `## Amendment — YYYY-MM-DD` section instead of editing the original; ADRs are append-only history. -->

## Revisit if

<!-- Optional. Explicit triggers that would re-open this decision. Each one concrete: a measurable signal, a closed upstream bug, a roadmap item. Skip if the decision is genuinely permanent. -->

- **<trigger 1>.** <action>
- **<trigger 2>.** <action>

## References

<!-- Optional. Upstream docs, related ADRs, YouTrack tickets, supporting living docs. Skip if the inline links in Context and Decision already cover everything. -->
