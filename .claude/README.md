# `.claude/` — agents, skills, commands

How Tether extends Claude Code. Two artifact homes, both creating a `/slash-command` interface; commands are the legacy shape, skills are the recommended forward path ([Anthropic docs](https://code.claude.com/docs/en/skills.md)).

- `.claude/skills/<name>/SKILL.md` — multi-step orchestrations and procedures with optional supporting files.
- `.claude/commands/<name>.md` — single-file prompt templates.
- `.claude/agents/<name>.md` — sub-agent definitions dispatched by orchestrating skills.

Skill, command, and agent inventories are surfaced automatically to every agent invocation — no manual index is maintained here.

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

Keep step prose a minimal directive checklist, not a tutorial. When a step always reduces to running a script, the whole step is one imperative line invoking it (marked mandatory where needed) — no inline bash block, no rationale narration, no walk-through of what the script does. Push the mechanics into the script and let hooks carry the invariants; a following agent has limited attention and does not need to think about plumbing the script already gets right. Reserve prose for *when* to run and *sequencing*, not *how* it works.
