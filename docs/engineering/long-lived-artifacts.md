# Long-lived artifacts — writing discipline

Rules for everything that outlives the task that birthed it: `CLAUDE.md`, `docs/`, `.claude/skills/**`, `.claude/agents/**`, `.claude/commands/**`, KDoc, inline comments, error messages.

These rules complement [`README.md` §Writing style](README.md#writing-style-for-these-guides) and apply to all long-lived prose, not only engineering guides.

## Rule, not its history

A long-lived artifact states what is / what to do / what equals — in present tense. No «after retro on #N», «found while working on X», «as discussed in #Y». No examples or rationale lifted from the specific task in which the artifact was born. Decision context lives in git and PR descriptions, not in the file.

If a rule is unreadable without referring to an incident, that's a weak formulation. Rewrite the rule; do not append the incident.

## Inline examples must be synthetic, not incident-rooted

Either generalise the *shape* of the error (contrast «principle catches this, but not this other thing that looks similar») so the example is synthetic and covers the class — or leave the example out entirely.

An incident-rooted example (a string lifted from the task that birthed the rule) is matched literally by the next agent, which then skips structurally identical neighbouring cases. If the rule isn't readable without an example, rewrite the formulation; do not prop it up with a quote from the incident.

## Runtime claims are snapshots, not rules

A long-lived artifact stating «component X currently does Y» is a snapshot — it may have drifted since written.

When reading: verify against the code before acting on the claim.

When writing: prefer the product invariant («pairing keyed by stable device identity») over the description of the current implementation. If a runtime detail is unavoidable, keep the minimum needed for comprehension.

## Link over inline-copy

A long-lived artifact references rules / tokens / tables from another artifact — link (`see docs/engineering/X.md §Y`), do not embed.

Any copy desynchronises at the source's first change; the reader acts on a stale version and gets boxed into a frozen-checklist mindset instead of holistically applying the canon. If an artifact is unreadable without an inline copy, the referencing side's formulation is weak — rewrite it; do not prop it up with a copy.

A short summary attached to the link («see X (covers A / B / C)», «see X — about the four rules of Y») is the same class as inline-copy: it duplicates the linked doc's structure right next to the link, and it desynchronises the same way. Trust the link; if the reader needs more than the link text to decide whether to click, the link text itself is weak — rewrite it.

## External claims cite their source

When prose describes the behaviour of an external tool, 3rd-party API, library, platform SDK, or specification — link to the authoritative doc next to the claim. Do not write from memory. External tools change; memory drifts; an unverified claim becomes permanently wrong and propagates through every artifact that references it.

If the authoritative doc does not cover what you need — say so explicitly rather than presenting a project-invented rule as the tool's behaviour.
