Repo-specific values for this project live in `.claude/project.json` — consult it; references below name their config keys.

Read-only sweep for issue #<N>. Return a compact digest — binding constraints and relevant paths, no file dumps:

- **Product features** — list the features dir (`docCorpus.featuresDir`) (+ its `README.md` index). Slug(s) matching this issue; binding constraints from each feature spec / UX brief (`docCorpus.featureSpec` / `docCorpus.uxBrief`) in 1-2 lines.
- **Product context** — the product docs root (`docCorpus.productDir`). The framing that binds this issue's scope / audience / timing.
- **Engineering living docs** — the engineering docs root (`docCorpus.engineeringDir`). Present-tense rules whose topic matches the task; flag any rule the planned change would violate.
- **ADR** — the ADR dir (`docCorpus.adrDir`). ADRs matching the topic; for each, its **Revisit if** section and whether this task trips a trigger.
- **Knowledge** — the knowledge dir (`docCorpus.knowledgeDir`). Solved-problem notes relevant to the task.
- **Glossary** — the glossary (`docCorpus.glossary`). Terms this issue's domain touches, with their locked definitions.
- **Prior `#<N>` mentions** — ranked list of file:line hits across the repo with one-line summary of what each expects. Every hit must be addressed in this PR or explicitly deferred to another issue.

For each layer in `docLayers`, note whether the target artifact exists, is a stub, or has open questions. Flag whether a doc already covers the subsystem this task targets.

Explicitly list all the relevant open questions.
