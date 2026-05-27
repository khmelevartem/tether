---
name: create-issue
description: Create a GitHub issue via `gh` CLI. Use when the user asks to file a task, open a ticket, or mentions `gh issue create`. Single issue or epic + sub-issues.
---

# Create Issue

## Body responsibility

The body answers **what** and **why**, plus where to start looking. **How** is decided later, during implementation, against `docs/engineering/`. Pre-baked APIs, exhaustive Affected modules, file-by-file DoD, Error handling, Non-functional requirements become hard constraints on the implementer and pollute their context with details that drift from reality the moment design diverges — keep them out. If the user dictates a specific approach in chat, capture it as one Entry-point line, not as a contract.

## Issue language

Default — **English**. Titles and bodies are in English; technical terms (class names, files, flags, APIs) stay in English as-is. Switch language only on explicit user request or in a non-English repository (confirm in one sentence first).

## When to apply

- User says "create an issue / task / ticket", "file a task on github", "open an issue about X".
- User describes a feature or bug and implies it goes into the tracker.
- User asks to split a large task into an epic + children.
- User mentions `gh issue create`.

Do not apply for **discussion**, writing into a spec document, or creating a PR — those are different.

## Process

1. **Recon** (cheap; skip nothing unless the user said "don't dig").
2. **Interview** — only the gaps. Goal-level questions, not implementation-level.
3. **Draft** — in chat, English, following [TEMPLATE.md](TEMPLATE.md). No file write, no `gh` call yet.
4. **Glossary review** — dispatch `review-glossary` on the draft body.
5. **Approval** — show draft, wait for OK or edits.
6. **Create** — `gh issue create --body-file /tmp/issue-body.md --label size:<S|M|L>,<feature|bugfix|refactor|infra|docs|dependency>`.
7. **Relationships** — link parent / blocked-by / blocks via native GitHub fields (sub-issues GraphQL, dependencies REST).
8. **Return** the issue number + URL.

Never create an issue without explicit draft approval — `gh issue create` is a side effect.

If the user's request is itself a complete draft (all sections present, no gaps), skip steps 2–3 and go to glossary + approval directly.

## Recon

Two cheap reads before the interview. They cut the interview in half.

### Project conventions

If unread in this session: `CLAUDE.md`, and the topical [`docs/engineering/`](../../../docs/engineering/) doc matching the task area. These set test commands, conventions, and existing rules — the DoD inherits from them.

### Similar closed issues

```bash
gh issue list --state closed --search "<keywords>" --limit 5 --json number,title,url
```

Worth 1–2 hits — link them in the body as references so the implementer has a ready precedent.

### Feature spec

For FEATURE tasks: `ls docs/product/features/`. If a spec exists for this feature, **read it** — Acceptance Criteria and Out of scope come directly into the issue. Spec wins over user description on conflict; clarify before drafting.

## Interview

Group questions into one pass, ≤4 at a time. List what is already clear so the user sees you read the request. Ask only about gaps.

Mandatory before drafting:

- **Repository** — `owner/repo`. Default: `gh repo view --json nameWithOwner`.
- **Task type** — one of `feature | bugfix | refactor | infra | docs | dependency`. Applied as a label, not a body field. Reviewers read it from the label.
- **Goal** — what changes for the user / system after this is done **and why it matters** (what problem it solves, what gets unblocked). One or two sentences covering both — the "why" is the spine the body hangs on.
- **Product-level DoD** — what observable behaviour proves it's done. (Not "class X exists", not "test Y passes" alone; "saved name survives app restart on all 4 platforms".)
- **Entry point** — one landmark from recon (file, module, precedent issue, doc section). If recon turned up nothing, say so explicitly in the body — the implementer must not guess whether the author looked.
- **Out of scope** — at least one bullet on a non-trivial task. Pre-empts scope creep.
- **Relationships** — parent epic, blocks, blocked-by. Users routinely forget to mention.

Do **not** ask for: precise file paths, exact API signatures, layering choices, error-handling strategy, non-functional thresholds unless the user volunteers them. Those are implementation-time decisions and pre-baking them turns the body into a fragile contract.

If the user already answered something in the request — don't re-ask.

## Draft

Follow [TEMPLATE.md](TEMPLATE.md). Mandatory body sections: Context, Goal, Entry point, DoD, Out of scope. Optional: References (similar closed issues), Hypotheses (bugfix only). Type is set via a type label (`feature` / `bugfix` / `refactor` / `infra` / `docs` / `dependency`), not a body field.

Mark `[?assumption: …]` wherever you filled something the user didn't state — they correct at review.

Show the draft in chat as markdown. Do not write to disk before approval.

## Glossary review

After drafting, dispatch the `review-glossary` sub-agent on the draft body (pass the prose string in the prompt — there's no diff yet). Turn drift flags into edits. If a term is missing from `docs/glossary.md`, ask the user whether to add an entry now or treat it as task-local.

## Create

Save body to `/tmp/issue-body.md`. Use `--body-file`, not `--body`, to survive special characters.

```bash
gh issue create \
  --title "<title>" \
  --body-file /tmp/issue-body.md \
  --label "size:<S|M|L>,<feature|bugfix|refactor|infra|docs|dependency>"
```

Both labels are mandatory: one `size:<…>` and one type label. Optional flags: `--assignee`, `--milestone`, `--project`. Apply extra labels only if they exist in the repo (`gh label list`).

`gh` prints the URL — keep it for relationship linking.

## Relationships

Native GitHub fields, never a `**Relationships:**` text block in the body. The body drifts; native fields don't.

- **Sub-issue (parent → child)** — GraphQL `addSubIssue` mutation.
- **Blocked by / blocks** — REST issue-dependencies: `POST /repos/{owner}/{repo}/issues/{n}/dependencies/blocked_by` with `{"issue_id": <database_id>}`. Note: GraphQL `addIssueDependency` does not exist — don't try it.
- **Related** — a `#N` mention inside Context or Goal, where it reads naturally. GitHub creates the bidirectional link automatically.

Full command snippets — see [RELATIONSHIPS.md](RELATIONSHIPS.md).

## Title

Brief (4–10 words). Reads like a changelog entry — describes the **useful increment** after the task is done.

- No prefixes (`[FEATURE]`, `feat:`). Categorisation is via labels.
- Not a verb-led command ("Add X"). State the resulting capability.

Listed in sequence, closed-issue titles should read as project history.

## Labels

**`size:`** (mandatory): `size:S` ≤ 4 h, `size:M` ≤ 1 day, `size:L` ≤ 3 days. Larger than `L` → epic with sub-issues; raise this during the interview.

**Type** (mandatory): exactly one of `feature`, `bugfix`, `refactor`, `infra`, `docs`, `dependency`.

## What to avoid

- **Pre-baking design** — file paths as commitments, signatures dictating contracts, package placement for new top-level types. Those are `/implement` + architect decisions against `docs/engineering/architecture-principles.md`. Issue body: "touches the file-server boundary", not "lives in `com.example.network.foo`".
- **Code-term DoD** — "test passes", "class exists" alone. State what a user / observer sees.
- **Padding** — filling sections to fill them. Drop optional sections that have nothing to say.
- **Skipping Out of scope** on non-trivial tasks — even one bullet narrows the agent's path.
- **`**Relationships:**` text blocks** — duplicates native fields, drifts.
- **Creating without approval** — `gh issue create` is the side effect; always wait for OK.
- **Inventing files** the user didn't mention and that aren't obvious — write `(clarify during /implement)`.
- **Heavy bodies on `size:L`** — that's a signal to split, not to write more.
