# `.claude/` — agents, skills, commands

How Tether extends Claude Code. Two artifact homes, both creating a `/slash-command` interface; commands are the legacy shape, skills are the recommended forward path ([Anthropic docs](https://code.claude.com/docs/en/skills.md)).

## Skills index

`.claude/skills/<name>/SKILL.md` — multi-agent orchestrations and procedures.

- `code-review` — multi-agent review of a PR; fan-out + adversarial + GitHub post.
- `document` — docs-only issue orchestrator (spec / ux-brief / tech-doc / ADR / knowledge / `.claude` prompt).
- `github-issue-author` — strict-template GitHub issue creation via `gh`.
- `grooming` — close current sprint by reality + gap-analysis + draft next sprint.
- `implement` — issue-to-PR orchestrator (coder ↔ reviewers loop, smoke, PR).
- `progress` — RPG-themed project snapshot.
- `smoke-test` — runtime happy-path across platforms.

## Commands index

`.claude/commands/<name>.md` — single-file prompt templates.

- `check-review` — read latest PR comments and triage.
- `close-issue` — finish an issue and merge its PR.
- `progress-boring` — flat numbers variant of `/progress`.
- `quick-issue` — lightweight `/gh issue create` (vs the full `github-issue-author` skill).
- `rebase` — pull `origin/main` and assess semantic overlap.
- `retro` — retrospective on an issue + its PR.
- `sprint-pick` — what to take next from the current sprint.

## Agents index

`.claude/agents/<name>.md` — sub-agent definitions dispatched by orchestrating skills. Writing agents: `architect`, `spec-writer`, `ux-expert`, `coder`, `ui-expert`, `tester`, `bug-reproducer`. Reviewing agents: `review-{dod,guides,glossary,reuse,architecture,correctness,tests,platform,ux,design-system,visual,adversarial}`.

## Skill or command — checkable criteria

Prefer a **skill** if any of these holds:

- [ ] The artifact encodes a multi-step procedure or checklist (not a single fact or one-shot template).
- [ ] You want auto-invocation by description match — the user types a topic, not the literal name.
- [ ] The artifact needs supporting files (`assets/`, `templates/`, `scripts/`, reference docs).
- [ ] You need pre-approved tools (`allowed-tools` frontmatter) without per-use permission prompts.
- [ ] You need invocation control (`disable-model-invocation`, `user-invocable`).

A **command** is sufficient when **all** of these hold:

- [ ] Single-file prompt template, no supporting files.
- [ ] Manual invocation only — no benefit from description-triggered auto-invocation.
- [ ] No need for tool pre-approval or invocation restrictions.

Both create the same `/slash-command`. The criteria are about what the artifact needs, not what it does.

## Anti-patterns

- **Guidelines-only skill** — a skill body that contains only rules (`use these conventions…`) with no actionable task. Dispatched as a sub-agent, it has nothing to produce and returns empty. Either embed in a host skill that has the task, or convert to a doc and link from skills that need it.
- **Large reference material inline in `SKILL.md`** — every line is a recurring context cost. Keep `SKILL.md` under ~500 lines; move tables, examples, code samples to supporting files loaded on demand. (See [Anthropic skills docs](https://code.claude.com/docs/en/skills.md#add-supporting-files).)

## Tone for prompt prose

[CLAUDE.md §Code style](../CLAUDE.md#code-style) and [`docs/engineering/long-lived-artifacts.md`](../docs/engineering/long-lived-artifacts.md) apply. Match siblings in the same folder.
