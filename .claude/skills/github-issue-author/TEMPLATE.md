# GitHub Issue Template

Full template and explanation of each section.

## Title

Brief (usually 4-10 words), describes a **useful increment** to the project — what will exist after the task is done. Reads like a changelog entry or a commit in the style of "what was added / what was fixed."

**No prefixes** like `[FEATURE]`, `[BUG]`, `feat:`, `task:`. Categorization — via labels, not the title.

Test: if you list the titles of all closed issues in sequence — they should read as the history of the project's development.

**Good:**
- `Magic-link email authentication`
- `Client-side search results caching`
- `PDF export from order card`
- `DB migration rollback on init-container failure`

**Bad:**
- `[FEATURE] Add authentication` — prefix, verb "add"
- `Fix bug` — does not describe the increment
- `Authentication` — too broad, unclear what exactly will appear
- `Implement AuthService class` — about the implementation, not the increment

## Task size

GitHub has no native "size" field at the issue level — the de facto standard is a **label** of the form `size:S`, `size:M`, `size:L`. Use that when creating via `--label`.

Scale: `S` — up to 4 hours, `M` — up to one day, `L` — up to three days. Anything larger than `L` is a signal to break it into an epic with sub-issues.

If the repository has a GitHub Projects with a custom "Size" or "Story Points" field — it can be set after issue creation via `gh project item-edit` (requires `gh auth refresh -s project`). Ask the user whether this is needed; do not do it by default.

## Full description template

```markdown
**Type:** FEATURE | BUGFIX | REFACTOR | INFRA | DOCS | DEPENDENCY

## Context

One or two sentences: which part of which application. Where in the system this task lives.

## What to do

One or two sentences: what exactly to do. What to add, fix, change, or remove.

## Why

In detail (~5 sentences, no strict limit): what problem it solves, why now, optionally — where the request came from. Terms with definitions and useful links (RFC, design document, chat discussion) — here too.

## Blockers and external conditions

What must be ready/available at the time of execution: external services, credentials, decisions from adjacent teams, another PR merged, a migration run.

## How it should work

### Main scenario

Step-by-step or in prose — what happens in the typical case. What the user (or calling code) sees/does, what the system responds.

### Edge cases

What to do if: empty input, network failure, concurrent requests, expired token, missing permissions, limits. List only the relevant ones — 3 real ones are better than 10 invented ones.

### Continuation of work

If the task is part of a larger flow: what happens next, who picks it up, what are the next steps for the user/system.

## Contract

The exact shape of what should exist after the task. This removes the need for the implementer to guess signatures.

**Public API after the task** — TypeScript interfaces, Python function signatures, endpoint schemas, event schemas, CLI command formats. What calling code will see and use.

\`\`\`ts
// example: src/features/search/cache.ts (new file)
export interface SearchCache {
  get(key: SearchKey): CachedResult | null
  set(key: SearchKey, value: SearchResult): void
}
\`\`\`

**Changed external contracts** — what changes in existing public APIs, DB schemas, config formats, environment variables. If nothing changes — write "none."

**New/changed tables or collections** — schema diff, if the DB is affected.

If the task is purely visual or configurational and has no contract — the section can be omitted.

## Before starting

**Mandatory: produce an implementation plan before touching code.** Show the plan to the user and wait for approval. Do not start the task without plan approval.

The plan should reflect: which files/modules are affected, what the validation strategy is, what open questions from the task are resolved along the way. If something in the technical details looks questionable or conflicts with the guides — do not silently rework it; show the alternative in the plan.

This item is the default for the project; keep it in all tasks more complex than trivial. Remove only for `size:S` AND when the task is a pinpoint fix with no architectural decisions (typo, rename, dependency update).

Additional steps specific to the task:

1. Reproduce the problem step by step (for bugs).
2. Measure the current metric (for optimizations).
3. Confirm that the expected behavior is genuinely absent.

If reproduction/measurement fails — close as `outdated` and do not proceed.

### Hypotheses about the cause (for BUGFIX)

If the issue includes a section on possible causes / hypotheses / candidate explanations — format it explicitly as **unverified hypotheses**, not as fact. Use formulations like "possibly," "hypothesis," "candidate cause," number the items, and explicitly state at the top of the section:

> These are **unverified hypotheses** by the issue author. Before writing a PR the implementer must verify the actual root cause (via `/implement` → `bug-reproducer` agent, or by manually reproducing the bug and checking each hypothesis with a minimal experiment) and record it in a comment on the issue.

Without this note the implementer may treat the first hypothesis as fact and write a fix that looks correct but does not solve the real problem. This has already happened (see retro on #71 / #47).

## Technical details

### Affected modules

List of classes / modules / files / endpoints / DB tables expected to be touched. If not known precisely — state the presumed location and mark `(to confirm)`.

**Pre-bake behaviour and contracts, not packages or layer placement.** File paths and module names are landmarks for the implementer to verify, not architectural commitments. Choosing the layer / package for a new top-level type, and the dependency direction between layers, is the architect's job during `/implement` (against `docs/engineering/architecture-principles.md`). Body wording: "touches the file-server boundary" — not "lives in `com.example.network.foo`". Naming a concrete package is fine for an existing type the task only modifies; for a new top-level type, leave placement to `/implement`.

### Code landmarks

(optional, but greatly helps the implementing agent)

**Similar implementations in the project** — point to files/modules where a similar pattern already exists. This helps avoid writing from scratch and keeps the project style consistent:

- `src/features/recentlyViewed/cache.ts` — similar LRU + sessionStorage idea, can be used as a reference

**Scouting commands** — `rg` / `grep` / `gh` commands that help the implementer quickly find the relevant places:

\`\`\`bash
rg "sessionStorage" src/features/    # all places where it is already used
rg "swr|stale-while-revalidate"      # whether the pattern already exists in the project
\`\`\`

### Non-functional requirements

Performance, security, compatibility, resource limits, logging/metrics requirements.

### Error handling

What errors can occur, how they are handled, what to log, what to show the user.

## Out of scope

Explicit boundaries — what is **not** part of this task, even if it might seem like it is. This prevents PR scope creep and pre-empts "I'll fix this along the way."

- What we are not doing (with explanation if non-obvious).
- Related tasks that have been moved to a separate issue (with numbers, if already created).
- Technical debt that is visible along the way but must not be touched now.

If the boundaries are trivial — the section can be omitted, but for tasks more complex than "fix a typo" it is usually worth filling in.

## Definition of Done

Formulations in the style of "can be verified yes/no," in terms of observable behavior or a measurable characteristic, not a structural artifact. A class / interface / method exists as a means of delivering behavior, but its mere existence proves nothing (empty implementation, not wired into DI). Describe what the user or an automated test will see.

✗ `DeviceNameStore with implementations on 4 platforms is in place` — structural, not observable.
✓ `The saved device name survives an app restart on all 4 platforms`.

Where possible — specify **concrete commands** to run, not descriptions. The implementer should be able to copy and execute.

- [ ] Implementation plan approved by the user before coding starts (see "Before starting")
- [ ] `<test command for affected files>` passes, covers: <list scenarios>
- [ ] `<linter command>` with no new errors
- [ ] `<e2e/integration test command, if applicable>` passes
- [ ] Manual check: `<step-by-step scenario or link to demo environment>`
- [ ] Documentation / changelog / updated ENV — if applicable

Commands are taken from `AGENTS.md` / `CONTRIBUTING.md` / `package.json` of the project (see "Scouting"). If no rules exist — write common ones (`pytest path/to/test.py`, `pnpm test`, `cargo test`) and mark `(confirm runner)`.

## References

Similar tasks in the project history — closed issues, PRs, discussions that serve as a good example. Found during the scouting phase.

- #42 — autocomplete caching (closed, pattern taken from there)
- #128 — LRU for recently viewed (closed)

## Consequences

Tasks that directly follow from this one but do not fit within its scope. Future issues to be created after the merge.
```

**Relationships (parent / blocked-by / blocks) are not written in the body** — they are set via native GitHub fields: sub-issues (GraphQL) + Relationships, known in REST as issue dependencies (REST `POST /repos/{o}/{r}/issues/{n}/dependencies/blocked_by`). See the "Issue relationships" section in [SKILL.md](SKILL.md). Only the task itself remains in the issue body.

"Related" — a mention of `#N` is embedded in the "Context" or "Why" section where it fits naturally by meaning, or expressed through a shared parent/labels.

## What matters when filling in

- **Context** — a broad stroke, not a retelling of the README. A coordinate of "where we are now."
- **Why** — this is the main value for the implementer. Not "we need to do X," but "without X, Y happens, which prevents Z."
- **Contract** — even if the user did not provide exact signatures, **propose them in a draft** based on the "Why" section. The user will correct them. That is still better than leaving the guesswork to the implementer.
- **Technical details** — a hypothesis, not an order. The implementer may choose a different path but has something to start from.
- **Out of scope** — even 1-2 points is better than an empty section. "We are not touching the server cache," "We are not refactoring X beyond what is necessary" — that already significantly narrows the scope for the agent.
- **DoD** — specifics win. "`pnpm test path/to/cache.test.ts` passes" is better than "unit tests exist." Commands are taken from `AGENTS.md` if it is present.
- **Paired sides of the contract — in one task.** If a fix/feature is logically two-sided (client↔server, sender↔receiver, producer↔consumer, write-path↔read-path), both sides must live in the same issue and be in the DoD of the same task. Do not move "the other half" into a doc remark, a separate issue, or a TODO in code — a server-side guard for which the client does not set a precondition physically does not trigger in production, and this is only discovered when an incident has already occurred. The sign that a task is two-sided: "server X validates Y" / "receiver checks Z, sent by sender" / "consumer expects field N, produced by producer." If "What to do" mentions only one side — verify that the other is either already in main or included in this same task.

## What to skip for small tasks (size:S)

For tasks up to 4 hours, some sections are unnecessary weight. Rule: if a section takes more time to write than the task takes to execute — skip it.

**Can be skipped entirely:** Blockers, Contract (if no API changes), Code landmarks, Non-functional requirements, Continuation of work, References, Consequences.

**Before starting** — for trivial fixes (typo, rename, dependency update) can be skipped. Otherwise keep at least the default item about the plan + approval.

**Can never be skipped:** Context, Why, Main scenario, Edge cases (at least 2-3), Out of scope (at least 1-2 points if the task is non-obvious), DoD (including the plan approval item).

For `size:S` the reasonable minimum is Context + Why + How it should work + Out of scope (1-2) + DoD (3-4 concrete points).
