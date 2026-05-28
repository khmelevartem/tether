# Long-lived artifacts — writing discipline

Rules for everything that outlives the task that birthed it: `CLAUDE.md`, `docs/`, `.claude/skills/**`, `.claude/agents/**`, `.claude/commands/**`, KDoc, inline comments, error messages.

These rules complement [`README.md` §Writing style](README.md#writing-style-for-these-guides) and apply to all long-lived prose, not only engineering guides.

## Rule, not its history

A long-lived artifact states what is / what to do / what equals — in present tense. No «after retro on #N», «found while working on X», «as discussed in #Y». No examples or rationale lifted from the specific task in which the artifact was born. Decision context lives in git and PR descriptions, not in the file.

If a rule is unreadable without referring to an incident, that's a weak formulation. Rewrite the rule; do not append the incident.

## Code does not belong in long-lived artifacts

A long-lived artifact describes a mechanism conceptually — what something is, why it exists, what invariants hold. It does not name the classes, methods, or library calls that implement it; those live in the code, where readers can see them in context. Class names in backticks, method signatures, and concrete API calls inside prose are signs the rule is being expressed through implementation, not as a rule.

If you find a code symbol in a long-lived artifact, check whether the surrounding sentence carries the same meaning with the symbol removed. Almost always it does — the symbol was leakage from the writing session, not a load-bearing reference. Remove it. Keep a code symbol only when the rule is genuinely about that symbol (e.g. a glossary entry whose subject is the symbol, or a doc whose subject is the library binding).

This applies to interfaces the same artifact is defining right now. A Rules section that says «`X.openOutput` throws on path traversal» locks the rule to a name that is one rename away from invalidating the doc. State what the seam *guarantees* — «the storage seam rejects path traversal at the boundary» — and let the code carry the verb. Same trap as runtime claims (§Runtime claims are snapshots), applied at the moment of writing rather than after drift.

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

## Refer to a slot functionally; link to the canon, never quote it

A long-lived artifact that needs to mention a domain concept — a UI element, a component, a state's visual realisation, an external artefact — does two things together: it names the slot by its **function** (`progress bar`, `searching indicator`, `error illustration`), and when more detail is needed it **links** to the single canonical document, never embedding a description.

This is the umbrella that two related rules ([Link over inline-copy](#link-over-inline-copy) above and the partner half below) jointly enforce. Taken together it gives three properties at once:

- **Single source of truth.** The canon lives in one place; everything else points at it.
- **Layer separation.** The functional name belongs to the layer where the slot exists (product / brief / API surface); the realisation belongs to its own layer (UI / implementation / spec) and changes without affecting the upstream artifacts.
- **Resilience to redesign.** When the realisation behind a slot is replaced, no other artifact has to be touched — neither for the name (functional name is independent of form) nor for the description (there was no description to update).

The cost of violating this rule is the cleanup ratio: a proper name (or an inlined description) embedded across N long-lived artifacts requires N edits when the realisation changes, even though nothing about the slot itself shifted. In practice this ratio easily reaches 10× the original adoption cost.

Functional name vs proper name — examples of the wrong and the right form:

| Wrong (proper name / inlined form)                                          | Right (functional name)             |
|-----------------------------------------------------------------------------|-------------------------------------|
| `the •—• mark in transferring state (line fills L→R)`                       | `the transfer progress bar`         |
| `the •—• in its searching state (hollow right dot, opacity oscillation)`    | `the animated searching indicator`  |
| `the Bélo logo's left lobe`                                                 | `the brand mark's identity region`  |

If a proper name unavoidably must appear (the artifact is specifically *about* it), keep the mention to that one canonical document and link from elsewhere.

## External claims cite their source

When prose describes the behaviour of an external tool, 3rd-party API, library, platform SDK, or specification — link to the authoritative doc next to the claim. Do not write from memory. External tools change; memory drifts; an unverified claim becomes permanently wrong and propagates through every artifact that references it.

If the authoritative doc does not cover what you need — say so explicitly rather than presenting a project-invented rule as the tool's behaviour.
