---
name: grooming
description: Run a backlog grooming session — close the current sprint-NN.md by actuals (task statuses, extra results within the window), go through open issues, find unfiled tasks via gap-analysis (platform symmetry, TODOs in code, MVP blockers from the roadmap) and compose a compact candidate for the next sprint in a fixed format (directions tag / composition / what it unblocks / optional merge order). Use when the user says "groom", "grooming", "plan the sprint", "close the sprint".
---

Repo-specific values for this project live in `.claude/project.json` — consult it; references below name their config keys.

Run a backlog grooming session and compose a candidate plan for the next sprint.

---

## Step 0 — Close the previous sprint by actuals

Before planning the next sprint, bring the current one to "actuals recorded" state. Without this, the next planning works from stale context.

**0.1 Find the current sprint doc.** `ls docs/sprints/` — the last file by number. If the folder doesn't exist or the last sprint is already marked as "closed/done" in all rows — skip Step 0.

**0.2 Read it** and identify the list of issues from the "Composition" section.

**0.3 Check the actual state of each task:**

```bash
gh issue view <N> --json state,title
```

For those where `state` ≠ `CLOSED` — also check the PR via `gh pr list --state all --search "<N>"`, because a PR can be merged without auto-closing the issue.

**0.4 Find tasks that closed within the sprint window but were **not** in the original composition.** These are "extra results" — work done beyond the plan.

```bash
# Date of start/merge of tasks from the composition — a reference for the window
git log --oneline --since="<date-of-first-sprint-commit>" --until="<date-of-last-sprint-commit>" | grep -E "^[a-f0-9]+ #[0-9]+:"
```

**Filter by merged PR, not by closed state.** `state=CLOSED` covers three different things, only one of which is a real deliverable:

- ✅ closed by a merged PR — real result, goes into the "Additional" section.
- ❌ manually closed as `not planned` / scoped out / deferred — not a result; goes nowhere.
- ❌ manually closed as `completed` without a PR — usually means superseded by another issue (rolled into its PR), abandoned, or paused. Not a separate deliverable. If it was truly absorbed by another task's PR, mention it inline on that task's line ("заодно поглотил #N"), not as its own bullet.

For every candidate from the closed-in-window list, verify `gh pr list --search "<N> in:title" --state merged` returns a PR before adding it. No PR — no line in "Additional".

**0.5 Update the sprint doc:**

- In the "Composition" table add an `Outcome` column with the status for each task: `✅ closed ([commit](url), PR #N)` / `🟡 partial (what remained)` / `❌ not done (reason)`.
- At the beginning/end of the table add one line **Total:** `X/Y tasks closed` plus a short "all goals achieved" / "partially, Z remains".
- Add a section **"Additional results"** or **"Outside original composition"** — issues closed in the sprint window but not planned. Each one — one line: `#N (commit, PR) — what it was and why it matters for feature progress`.
- In the "Useful increment" section (or equivalent) — change future tense to past: "After sprint X will become..." → "X done: ...". Add an "Additional" subsection with the extra results.
- In the "Related product specs" section — add any specs that were edited within the sprint window outside of the composition.

**Style:** do not rewrite the sprint goal, do not edit "Intentionally not included" — that is a historical snapshot of the decision at planning time. Add actuals on top of the plan, don't replace it.

**0.6 Report the closing result to the user:** "Sprint N: X/Y tasks closed, Z additional results in the window. Sprint doc updated. Moving on to planning the next one."

**0.7 Recalibrate the cost bands.** `close-issue` reconciled each merged task's `size:` against its actual review burden, so the closed set is now ground truth for what `S`/`M`/`L` cost. Regenerate the per-size means that `progress` uses for task cost:

```bash
python3 .claude/scripts/review-burden.py --write
```

This rewrites [`.claude/sizing-bands.json`](../../sizing-bands.json); commit it if the numbers moved.

---

## Step 1 — Product context

Read in sequence (under `docCorpus.productDir` unless noted):

1. `vision.md` — why the product exists
2. `README.md` — overview
3. `roadmap.md` — stages and priorities
4. `features/README.md` (under `docCorpus.featuresDir`) — list of features and their status

Goal: understand which roadmap stage we are at, what has already been implemented, what comes next.

---

## Step 2 — Review open issues

Work from general to specific — don't read details unless necessary.

**2.1 Get the list:**
```bash
gh issue list --state open --json number,title,labels
```

**2.2 For each issue read only the key sections** (context, goal, DoD, relationships):
```bash
gh issue view <N> --json title,body,labels
```
Look in body for sections: "Context", "Why", "To do", "DoD", "Relationships", "Blockers".
Details (comments, history) — only if blockers or dependencies are unclear.

**2.3 Build a mental map:**
- Which issues are blocked by others?
- Which ones touch the same part of the codebase (UI, network, discovery, protocol)?
- Which ones can be done in parallel without conflicts?

---

## Step 3 — Sprint readiness analysis

For each open issue assess:

| Criterion | Yes / No |
|----------|----------|
| No known blockers | — |
| Clear DoD exists | — |
| If a feature — spec exists under the features dir (`docCorpus.featuresDir`) | — |
| Spec referenced in the issue body | — |
| Does not conflict with other candidates in the codebase | — |

If there is no spec under the features dir (`docCorpus.featuresDir`) for new functionality — flag this explicitly as a risk.

---

## Step 4 — Finding unfiled tasks (gap-analysis)

Before composing the plan, check: **is everything meaningful at this point already filed as an issue?** The backlog lags behind the real state of the code — a task can be obviously needed but simply never recorded.

Systematically go through two axes:

### 4.1 Platform symmetry

The `vision.md` principle usually requires cross-platform parity. Compare the state of targets against each other:

```bash
# What exists on each target?
ls composeApp/src/{commonMain,androidMain,iosMain,appleMain,desktopMain,jvmMain}/kotlin/...
```

For each major component (`FileServer`, `FileClient`, `MdnsDiscovery`, `TrustedDeviceStore`, UI screens) make a table: **where implemented, where stub, where missing**. Stubs (classes throwing `error("not implemented")`) are formally "present" but factually a gap.

If you see asymmetry (a feature covered on 2 of 3 platforms) — check:
- Is there an open or closed issue for the lagging platform?
- If not — it's a candidate for a new issue.

### 4.2 Stubs and TODOs in code

```bash
rg "TODO|FIXME|not (yet )?implemented|stub" composeApp/src/ --type kotlin
rg "error\(\"" composeApp/src/ --type kotlin   # stubs throwing errors
```

Each such location is a potentially unfiled task. Don't file minor TODOs inside methods, but structural "implementation comes later" markers are candidates.

### 4.3 Roadmap blockers

Re-read `roadmap.md` (under `docCorpus.productDir`). For each MVP item check: is there an issue moving it forward? If an MVP item has no issue at all — that's a gap.

### 4.4 What to do with found gaps

List them for the user **before** composing the sprint plan:

> Before building the plan I found tasks that are conceptually needed now but not filed as issues:
>
> 1. **<Title>** — <one sentence: what and why>. Layer/platform: <...>. Size: <S/M/L>.
> 2. ...
>
> File them now via /create-issue so they become sprint candidates?

Don't file issues silently. The user decides: file now, defer, or add as a note to the plan without a formal issue.

If the user agrees — run the /create-issue interview for each task. Only after the issues are created do they become candidates for Step 5.

---

## Step 5 — Candidate selection

Pick 3–5 tasks that:
- Match the current roadmap stage
- Are not blocked — neither by external issues nor by each other within the sprint.
  **If #A blocks #B — both cannot be in the same sprint, even if #A is nearly done.** Chains are split across sprints: #A in this sprint, #B in the next. This rule is strict: "we'll finish #A quickly then take on #B" leads to assignee-B downtime and DoD failure.
- Can be done in parallel (different platforms, different codebase layers, different features)

Good combinations for parallel work:
- One iOS feature + one Android/Desktop feature
- A UI change + a network/discovery change
- A new feature + an unrelated bugfix
- Several features touching different modules

---

## Step 6 — Sprint doc format

Save to `docs/sprints/sprint-NN.md`. Format:

```markdown
# Sprint NN · <Skyrim-codename>

**Направления:** <area1> · <area2> · <area3>

## Состав

| # | Issue | Название | Тип | Размер |
| - | ----- | -------- | --- | ------ |

## Что разблокирует

<1–3 bullets. Only "after X, Y becomes possible" — not a paraphrase of what the task does. If a bullet starts with "after X shipped Y" without a downstream step — drop it.>

<!-- Optional: omit if there's no rebase risk. Heading is literal — no parenthetical suffix in the actual doc. -->
## Порядок мерджа

<#A → #B → #C; `||` marks parallel branches.>
```

Section names in the template — `Состав`, `Что разблокирует`, `Порядок мерджа`, the leading `**Направления:**` tag — are literal output strings written to the sprint doc as-is. Sprint docs in this repo are authored in Russian; the literals match that convention. Tooling matches them by exact string.

**Направления (Directions).** Short labels for the functional areas this sprint touches. Flat list separated by `·`. Not goals, not commitments — just a "where to look" tag. A sprint typically covers 2–4 directions; more signals tasks are diverging and the sprint is losing focus.

**Skyrim-codename.** A decorative subtitle in epic-fantasy style — for atmosphere and recognisability. **Carries no operational load.** Any tool reading `sprint-NN.md` must rely only on the number `NN`; the subtitle must not be parsed or analysed.

**Size (for the column):**
- **S** — isolated change with no platform specifics
- **M** — several files / one platform with nuances
- **L** — new component / multiple platforms / lots of unknowns

**What must NOT be in the sprint doc:**
- A prose "Sprint goal" paragraph. Sprints in this project = parallel agent loadout + merge order, not a customer-visible increment commitment. Directions tag + composition table + merge order carry all the load.
- Justifications for why a task was chosen ("priority", "because it blocks #N").
- Parallelism tables by layer, conflict matrices.
- External blocking chains — that goes in `## Что разблокирует`, in one sentence.
- A "not included intentionally" list — it lives in the issue backlog, not the sprint doc.
- A list of related product specs — specs are linked from issues, not duplicated here.
- Per-task DoD / acceptance criteria expansion.

When closing the sprint (Step 0), an `Итог` column is appended to the `## Состав` table, and a `## Дополнительные результаты` section is added — this is the only thing that grows on top of the plan.

If there are tasks among the candidates without a spec — explicitly ask before saving whether a spec needs to be created.
