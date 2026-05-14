# Architecture Decision Records

ADRs in this folder record one-time architectural choices: context, options considered, decision, and consequences. Each ADR is append-only history. Current state and conventions live in the parent [`docs/engineering/`](..) folder (architecture-principles, dependency-injection, modules, presentation-layer, ...).

Operational artefacts — agent definitions in `.claude/agents/` and skills in `.claude/skills/` — reference the living docs in `docs/engineering/`, not the ADRs. The ADR carries the *why* for human readers; the living doc carries the *what is currently true* that agents enforce.

For ADR conventions and templates, see [adr.github.io](https://adr.github.io/) and [Joel Parker Henderson's collection](https://github.com/joelparkerhenderson/architecture-decision-record). No need to copy them — pick a template that fits the decision.

Write an ADR when a non-trivial choice will be questioned by future contributors (library, framework, structural pattern). Skip it for naming or style — those belong in living docs.
