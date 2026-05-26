---
name: github-issue-author
description: Create a GitHub issue via `gh` CLI using a strict template — title as a useful increment, detailed description (context, scenarios, technical details, DoD, dependencies) and parent/blocking/blocked relationships. Use this skill whenever the user asks to create an issue, task, or ticket on GitHub, to formalise a feature/bug, to file a task in the repository, or mentions `gh issue create` — even if they don't name the template explicitly. Suitable for both single issues and a series of related tasks (epic + sub-issues).
---

# GitHub Issue Author

Skill for creating GitHub issues via the `gh` CLI using a template where the title reads as a useful increment to the project and the description contains enough context and acceptance criteria for the assignee to pick up the task without further clarification.

## Issue language

Default — **English**. Issue titles and bodies are written in English. Technical terms (class names, file names, flags, APIs) stay in English as-is.

Switch to a different language only if the user explicitly requests it ("write it in Russian", "make the issue in Russian"), or if the repository is explicitly non-English (README/issues/CONTRIBUTING in another language, team works in another language) — in that case, confirm the choice with the user in one sentence before drafting.

## When to apply

- The user says "create an issue / task / ticket", "file a task on github", "open an issue about X".
- The user describes a feature or bug and implies it should go into the tracker.
- The user asks to split a large task into a series of related issues (epic + children).
- The user mentions `gh issue create` or is editing an existing issue draft.

Do not apply if the user wants to **discuss** an idea, write a spec into a document, or create a PR (those are different).

## High-level process

1. **Project reconnaissance** — find `AGENTS.md` / `CLAUDE.md` / `CONTRIBUTING.md`, find 1–2 similar closed issues.
2. **Interview** — list what is already clear from the request and recon, and ask about gaps.
3. **Draft** — form a title and description according to the template (see [TEMPLATE.md](TEMPLATE.md)).
4. **Glossary review.** Dispatch the `review-glossary` sub-agent on the draft body. Turn drift flags into draft edits; if a term is absent from the glossary — ask the user whether to add an entry now or treat it as task-local.
5. **Review** — show the draft to the user **before creating the issue**, wait for approval or edits.
6. **Create** — `gh issue create --body-file`.
7. **Relationships** — link to the parent via `gh api` (sub-issues) or mentions in the body.

Never create an issue without explicit draft approval — `gh issue create` is a side effect the user must confirm.

Never write a draft before receiving answers to key interview questions. Exception: the user's request already contains a practically complete draft with all sections.

## Project reconnaissance

Before the interview, take two quick actions — they provide context without which the interview will be superficial and the issue will diverge from the project's style.

### Agent rules file

Check the repository for one of:
- `AGENTS.md` (emerging standard)
- `CLAUDE.md`
- `.cursor/rules/` (directory)
- `CONTRIBUTING.md`

If the file exists — **read it**. It tells you test/linter/build commands, commit conventions, and which folders to avoid — all useful for the "DoD" and "Out of scope" sections.

If no file exists — **note this to the user in one line** at the start of the interview: "The repository has no AGENTS.md / CLAUDE.md / CONTRIBUTING.md. A separate issue should be filed to create one — it greatly helps agents in future work. Do it now or later?" Don't insist, but mention it.

### Similar closed issues

Run 1–2 searches over closed issues to find references:

```bash
gh issue list --repo OWNER/REPO --state closed --search "relevant keywords" --limit 5 --json number,title,url
```

If you find clearly similar ones (same module, same area) — note their numbers. At the draft stage you'll add them to the "References" section. This gives the assignee **ready-made solution examples in this specific project** — far more valuable than general principles.

If nothing was found — that's fine, the "References" section can be omitted.

### Product documentation

If the repository has `docs/product/`, check for a feature spec for the task being created:

```bash
ls docs/product/features/
```

If a file for this feature exists — **read it**. It contains:
- Acceptance Criteria — copy directly into the issue DoD
- Out of scope — copy into the same-named section of the issue
- Technical Notes — use for the "Technical details" section

The feature spec takes priority over what the user described in the request — if there is a conflict, clarify with the user before drafting.

If `docs/product/features/` exists but there is no file for this feature — proceed as normal without mentioning this to the user.

### When to skip reconnaissance

- The user explicitly said "don't dig into the repo, I know what I'm doing"
- The repository is private and `gh` has no access (in this case, tell the user honestly and proceed to the interview)
- The request is about creating the very first issue in a new repository

## Gathering context

**Clarify gaps first, then write the draft.** The skill operates in "interview → draft" mode, not "draft with assumptions → revisions". This saves iterations: asking 2–3 questions is cheaper than rewriting a finished issue.

Before writing the draft, make sure you have answers to all these questions:

- **Repository** — `owner/repo`. If not specified, try to determine it via `gh repo view --json nameWithOwner` in the current working directory. If that fails — ask.
- **Task type** — `FEATURE | BUGFIX | REFACTOR | INFRA | DOCS | DEPENDENCY`. Determine from the description and confirm in the interview if unclear. Needed for the `**Type**` field in the template — the reviewer agent reads it when checking the PR.
- **What the app/project is** — in one sentence. Needed for the "Context" section.
- **Why this task is needed** — what problem it solves, what will improve after it's done. Without this the "Why" section will be empty.
- **How it should work** — the main scenario at least in broad strokes. You can propose edge cases yourself, but the main flow must be confirmed by the user.
- **Technical scope** — which files/modules are affected. If the user doesn't know or it isn't defined upfront — it's acceptable to leave `(clarify during breakdown)`, but **ask first**, don't assume silently.
- **Relationships** — is there a parent epic, what does this block, what blocks it. Ask explicitly, because the user often forgets to mention.
- **Labels / assignee / milestone** — are there standard ones for this repository. Optional, but find out if the user wants them.

### How to ask questions

Group into one pass. No more than 4–5 questions at a time — otherwise the user gets tired of answering. If there are more — split into two waves: first the most important (what is the project, why, main scenario), then details (modules, relationships, labels).

If the user's original request already answered some questions — don't ask them again, ask only about gaps. Explicitly list at the start what is **already** clear from the request, so the user sees you read and understood.

**Example of a good question:**

> From the request I understood:
> - repository `myorg/shop`
> - task is about caching search results on the frontend
> - problem — repeated request when returning from a product page
>
> Please clarify:
> 1. What is the cache TTL? (or is it "until the tab is closed")
> 2. Is there a parent issue / epic to link to?
> 3. Which labels to apply? (or none)

After receiving answers — move to the draft. Don't ask again questions that already have answers, don't stretch the interview.

### When to skip the interview

If the user's request already contains a detailed description with all sections (effectively a ready draft), and there are no obvious gaps — skip the questions step, go directly to formatting with the template. In this case, mark `[?assumption: ...]` in the draft where you filled in something yourself, and the user will confirm/correct at the review stage.

## Issue template

Full template and explanation of each section — see [TEMPLATE.md](TEMPLATE.md).

## Relationships between issues

**Principle: relationships are set through native GitHub fields, not text in the issue body.** The issue body stays about the task; relationships live in the API/UI and are visible as structured data (sub-issues sidebar, blocked-by/blocking sidebar, project fields). This gives the reviewer bot and automations a machine-readable dependency graph.

GitHub supports:

1. **Sub-issues / parent** — native mechanism via GraphQL (`addSubIssue` / `removeSubIssue`), visible in the UI as a hierarchy.
2. **Blocked by / blocks (Relationships in UI)** — native issue dependencies. Exposed via **REST**, not GraphQL: `POST /repos/{owner}/{repo}/issues/{issue_number}/dependencies/blocked_by` with body `{"issue_id": <database_id>}`. Shown in the UI in the issue sidebar as "Relationships". GraphQL mutation `addIssueDependency` **does not exist** — do not attempt to call it.
3. **Related** — no native field; use a `#123` mention in the body, a shared parent/epic, or label tags.

**Do not use a text block `**Relationships:**` in the issue body** — it duplicates what's already in native fields and drifts from them when edited. Exception: fallback if a specific mutation is unavailable in the repository (see below).

### Sub-issue (parent → child)

Sub-issues are created via `gh api` (the feature rolled out in 2024 and is available via GraphQL):

```bash
# Get node_id of parent and child issues
PARENT_ID=$(gh api graphql -f query='
  query($owner: String!, $repo: String!, $number: Int!) {
    repository(owner: $owner, name: $repo) {
      issue(number: $number) { id }
    }
  }' -F owner=OWNER -F repo=REPO -F number=PARENT_NUMBER --jq '.data.repository.issue.id')

CHILD_ID=$(gh api graphql -f query='
  query($owner: String!, $repo: String!, $number: Int!) {
    repository(owner: $owner, name: $repo) {
      issue(number: $number) { id }
    }
  }' -F owner=OWNER -F repo=REPO -F number=CHILD_NUMBER --jq '.data.repository.issue.id')

# Link child to parent
gh api graphql -f query='
  mutation($parentId: ID!, $childId: ID!) {
    addSubIssue(input: { issueId: $parentId, subIssueId: $childId }) {
      subIssue { number title }
    }
  }' -F parentId="$PARENT_ID" -F childId="$CHILD_ID"
```

If `addSubIssue` returns an error about an unknown field — the repository does not have sub-issues enabled. Notify the user and offer a fallback via mentions in the parent body.

### Blocked by / blocks (Relationships)

In the GitHub UI this field is called **Relationships**, in the REST API — **issue dependencies**. Use **REST**, not GraphQL.

```bash
# Get the database id (integer, the `id` field in the REST response, NOT node_id) of the blocking issue
BLOCKER_DB_ID=$(gh api repos/OWNER/REPO/issues/BLOCKER_NUMBER --jq .id)

# A blocks B  ⇄  B blocked by A
# Call on the blocked issue (B), pass the database id of the blocker (A)
gh api repos/OWNER/REPO/issues/BLOCKED_NUMBER/dependencies/blocked_by \
  -X POST -F issue_id=$BLOCKER_DB_ID
```

Check current relationships: `gh api repos/OWNER/REPO/issues/N/dependencies/blocked_by` (returns an array of issues blocking N).

If the endpoint returns 404 on `POST` — the Relationships feature is not enabled in the repository. Fallback: add one line `Blocked by #N` to the blocked issue body and ask the user to enable Relationships in repository settings.

### Related

No native field. Use:
- A `#N` mention in the **Context** or **Why** section of the issue (where it makes natural sense).
- A shared parent/epic, if the tasks are genuinely related hierarchically.
- Labels (e.g. `area:discovery`), if it's a thematic relationship rather than a task-to-task one.

Don't create a separate text block `**Relationships:**` at the end of the issue — the `#N` mention already creates a bidirectional link in the GitHub UI.

## Creating issues via `gh` CLI

### Preparation

1. Verify `gh` is authorised: `gh auth status`. If not — ask the user to run `gh auth login`.
2. Determine the repository. If working in a local clone — `gh issue create` will detect it automatically. Otherwise `--repo owner/repo` is needed.
3. Save the issue body to a temporary file (via the `--body-file` flag), not in `--body` — this won't break on special characters, quotes, and line breaks.

### Create command

```bash
# Save body to file (use the create_file tool, not heredoc, to avoid escaping issues)
# Path: /tmp/issue-body.md

gh issue create \
  --repo OWNER/REPO \
  --title "Cache search results on the client side" \
  --body-file /tmp/issue-body.md \
  --label "feature,frontend"
```

Optional flags:
- `--assignee @me` or `--assignee username`
- `--milestone "v1.2"`
- `--project "Roadmap"`

After creation `gh` outputs a URL — save it, it will be needed for linking sub-issues.

### Getting the number of the created issue

```bash
URL=$(gh issue create --repo OWNER/REPO --title "..." --body-file /tmp/issue-body.md)
NUMBER=$(echo "$URL" | grep -oE '[0-9]+$')
```

### Creating a series of related issues

If you need to create a parent + N children:

1. Create the parent first, record its number.
2. Create each child, record their numbers.
3. For each child call the `addSubIssue` mutation with parent_id and child_id.
4. Do **not** add a list of children manually to the parent body — the GitHub UI will render them automatically via the sub-issues API.

If the sub-issues API is unavailable — add a list to the parent body:

```markdown
**Child tasks:**
- [ ] #11 — Email authentication
- [ ] #12 — Password recovery
- [ ] #13 — Two-factor via TOTP
```

## Workflow when working with the user

1. **Reconnaissance** — check `AGENTS.md` / `CONTRIBUTING.md`, search for 1–2 similar closed issues. This takes 2–3 commands and greatly improves draft quality.
2. **Interview** — list what is already clear from the request and recon, and ask questions about gaps. No more than 4–5 questions at a time. Don't proceed to the draft until you receive answers.
3. **Draft** — title and full issue body **in English** (unless otherwise agreed), as markdown in the chat. Don't create a file or call `gh` at this step. Mark `[?assumption: ...]` for remaining uncertainties. For the template — see [TEMPLATE.md](TEMPLATE.md).
4. **Glossary review** — dispatch the `review-glossary` sub-agent on the draft body. Turn drift flags into edits; undocumented terms — ask the user whether to add an entry now.
5. **Review** — wait for approval or edits. If there are many edits or they affect scope — it may be worth re-asking a couple of interview questions.
6. **Create** — `gh issue create --body-file`. Show the result URL.
7. **Relationships** — sub-issues via API, mentions via body editing (`gh issue edit NUMBER --body-file ...`).
8. **Summary** — list of created issues with numbers and URLs.

## Examples

Full issue creation examples — see [EXAMPLES.md](EXAMPLES.md).

- Example 1: single issue, feature
- Example 2: epic + sub-issues

## What to avoid

- **Don't create an issue without draft approval**, even if the request seems unambiguous.
- **Don't skip reconnaissance** — `AGENTS.md` and similar closed issues usually provide 80% of the context the user would otherwise supply manually.
- **Don't pad the body** just to fill all sections — skip optional ones if there's nothing to say. A missing section is better than one filled with "none" or "n/a".
- **Don't leave DoD abstract** — if the repository has `AGENTS.md` or `package.json` / `pyproject.toml`, take commands from there. "Unit tests exist" is not a DoD.
- **Don't invent Out of scope just to fill the section** — but don't leave it empty on complex tasks. One or two points are almost always there.
- **Don't use prefixes** in titles (`[FEATURE]`, `feat:`). Categorisation — via labels.
- **Don't put the body in `--body`** via CLI directly with large text — use `--body-file`.
- **Don't apply labels that don't exist in the repository** — first check `gh label list --repo OWNER/REPO` if unsure.
- **Don't invent files and modules** if the user didn't mention them and they're not obvious from the repository. Better to write `(clarify during breakdown)`.
- **Don't ignore `size:L`** — it's a signal to "split into an epic", not "write a big issue".
