# GitHub Issue Body Template

The lean shape. Mandatory body sections in order: **Context → Goal → Entry point → Definition of Done → Out of scope**. Everything else is optional and dropped when there's nothing to say. Task type and size live on the issue as labels (`type:<…>`, `size:<…>`), not in the body.

The body answers **what** and **why**, plus where to start looking. It does not pre-bake **how** — that's `/implement`'s job.

## Template

```markdown
## Context

One or two sentences. Which subsystem / feature this lives in. A `#N` mention of the parent epic or related issue if useful.

## Goal

One or two sentences: what changes for the user or the system after this is done. Phrased as the resulting capability, not the action.

## Entry point

One line: where to start investigating — a file, a module, a closed precedent issue, a doc section. Landmarks, not commitments. If recon turned up nothing, say so explicitly: "no precedent; start from scratch." The line is mandatory so the implementer never has to guess whether the author looked.

## Definition of Done

Verifiable, **product-level** behaviour. What an observer sees. Commands and structural artifacts are allowed only where they directly evidence the behaviour.

- [ ] <observable behaviour 1>
- [ ] <observable behaviour 2>

## Out of scope

Explicit boundaries. Pre-empts "I'll fix this along the way."

- <what we are not doing, with one-line reason if non-obvious>
- <related task moved to a separate issue, with `#N`>
```

## Optional sections

Add only when they carry information the implementer can't derive:

- **References** — `#N` of closed issues with similar pattern. Found via recon.
- **Hypotheses** — BUGFIX only, when the reporter suspects a cause. **Mark explicitly as unverified** — the implementer must confirm the root cause through a reproduction step before any fix. Use "possibly", "candidate", "hypothesis"; number the items. Without the unverified marker the first item gets treated as fact and the fix targets the wrong thing.
- **Consequences** — follow-up tasks for after this merges. One line each.

Sections that **never** appear in the body:

- Contract / API signatures / interface bodies — `/implement` decides shape.
- Affected modules with package paths — landmarks go in Entry point as prose.
- Code landmarks with `rg` / `grep` commands — Entry point line suffices.
- Non-functional requirements — only if the user explicitly stated a threshold; otherwise `/implement` consults `docs/engineering/`.
- Error handling — same; an architecture concern, not an issue-body concern.
- `**Relationships:**` block — native GitHub fields only.

## Title

Brief (4–10 words). Reads as the useful increment — what exists after the task.

**Good:** `Magic-link email authentication`, `Client-side search results caching`, `DB migration rollback on init-container failure`

**Bad:** `[FEATURE] Add authentication` (prefix + verb), `Fix bug` (no increment), `Implement AuthService class` (about implementation, not capability).

Test: closed-issue titles listed in sequence should read as project history.

## Labels

Both mandatory; pass via `--label "size:<…>,type:<…>"` on `gh issue create`.

- **`size:`** — `size:S` ≤ 4 h, `size:M` ≤ 1 day, `size:L` ≤ 3 days. Larger → epic with sub-issues; raise during interview.
- **`type:`** — exactly one of `type:feature`, `type:bugfix`, `type:refactor`, `type:infra`, `type:docs`, `type:dependency`. Reviewers and `/implement` branch on it.

## What "product-level DoD" means

| Code-term (avoid) | Product-level (use) |
|---|---|
| `DeviceNameStore with 4 actuals exists` | Saved device name survives app restart on all 4 platforms |
| `pnpm test src/cache.test.ts passes` | Returning from product page to search results shows the previous list instantly (no API call in first 100ms) |
| `New endpoint `/v2/search` returns 200` | Search query returns matching results within 200ms p95 |

Commands are fine where they're the cheapest evidence of behaviour (`./gradlew allTests -q passes` for a `BUGFIX` whose evidence is a failing-then-passing test). Avoid commands as a stand-in for the behaviour itself.

## Paired sides of a contract — in one task

If a fix or feature is logically two-sided (client↔server, sender↔receiver, producer↔consumer, write-path↔read-path), both sides live in the **same** issue and the **same** DoD. A server-side guard for which the client never sets a precondition does not trigger in production — and you only learn this from the incident. Sign of a two-sided task: "server X validates Y" / "receiver checks Z, sent by sender". If Goal mentions only one side, verify the other is either in main already or included here.

## Out of scope is mandatory

For any task more involved than a typo fix. Even one bullet narrows the implementer's path and prevents PR scope creep. If the user gave no boundary, ask in the interview.

## Relationships

Set via native GitHub fields (sub-issues GraphQL + dependencies REST), not body text. See [RELATIONSHIPS.md](RELATIONSHIPS.md) and [SKILL.md](SKILL.md) §Relationships.
