Read-only sweep for issue #<N>. Return a compact digest — binding constraints and relevant paths, no file dumps:

- **Product features** — `ls docs/product/features/` (+ `README.md` index). Slug(s) matching this issue; binding constraints from each `spec.md` / `ux-brief.md` in 1-2 lines.
- **Product context** — `docs/product/*.md`. The framing that binds this issue's scope / audience / timing.
- **Engineering living docs** — `docs/engineering/*.md`. Present-tense rules whose topic matches the task; flag any rule the planned change would violate.
- **ADR** — `docs/engineering/adr/adr-*.md`. ADRs matching the topic; for each, its **Revisit if** section and whether this task trips a trigger.
- **Knowledge** — `docs/knowledge/*.md`. Solved-problem notes relevant to the task.
- **Glossary** — `docs/glossary.md`. Terms this issue's domain touches, with their locked definitions.
- **Prior `#<N>` mentions** — ranked list of file:line hits across the repo with one-line summary of what each expects. Every hit must be addressed in this PR or explicitly deferred to another issue.

For each layer in `docLayers`, note whether the target artifact exists, is a stub, or has open questions. Flag whether a doc already covers the subsystem this task targets.

Explicitly list all the relevant open questions.
