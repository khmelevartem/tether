---
name: close-issue
description: Finish a GitHub issue and merge its PR — pull main, walk acceptance criteria, gate on manual smoke + user confirmation, ask one interview-prep comprehension question, sweep PR review comments, update docs touched by the change, record any engineering decisions made in chat, post-factum size label, squash-merge, follow-ups, optional retro. Use when the user says "close issue N", "merge the PR", "finish task N", "ship #N", or invokes `/close-issue`.
---

Complete task <issue number> and merge the PR.

Work strictly step by step. Each step is a stop point: if something is not done, report it explicitly and wait for the user's confirmation or correction. Do not proceed to the next step without an explicit OK.

---

## Step 0 — Pull main

Run `/pull-main` — it will pull `origin/main` and assess semantic overlap with the current work. If the step reports significant overlap — adjust the work to the new context before starting AC, smoke, review.

---

## Step 1 — Acceptance Criteria

Obtain the list of acceptance criteria:
- from the issue (DoD / Acceptance Criteria section)
- from the feature spec in `docs/product/features/`, if there is a file for this task

**If neither a DoD nor a feature spec exists** (typical for infrastructure and meta tasks) — extract the goals from the issue body, formulate a verifiable checklist yourself, and explicitly show it to the user with the note "AC extracted from issue body, confirm or adjust". Do not proceed to the next step until the user has confirmed.

For each criterion, explicitly state the status: ✅ done / ❌ not done / ❓ impossible to verify automatically.

If there are ❌ items — stop. Do not continue until they are resolved.

**Checking warnings.** The `-q` flag hides Gradle/KGP warnings. Run one pass without it and verify that no new warnings have appeared:

```bash
./gradlew allTests 2>&1 | grep -i "warning\|warn" | grep -v "^w: KLIB"
```

Warnings of the form `w: KLIB resolver: The same 'unique_name=...'` are pre-existing — ignore them. All others — investigate before merging.

---

## Step 2 — Manual tests

### 2.1 Smoke-test — at your discretion

The repo has a `/smoke-test` skill (see `.claude/skills/smoke-test/`). Decide yourself whether to run it before requesting confirmation and which blocks make sense for this task.

Guideline: which parts of the skill genuinely intersect with what changed in the PR. If FileServer/CLI changed — run the Desktop blocks. If Android FGS / mDNS changed — run the Android block. If native source sets changed — the compile block. DOCS-only / `.claude/` / comments — usually nothing needs to be run.

If you ran it — attach the verdict (🟢/🟡/🔴) and the list of blocks to the confirmation request in 2.2.

### 2.2 User confirmation

Ask the user explicitly:

> Are all manual checks done? If not — what remains?

**This is a hard stop-point.** Do not proceed to step 2.3 until the user has replied with an explicit "OK / done / yes". A successful smoke run of your own does not count as confirmation — it only increases the user's confidence when answering.

### 2.3 Comprehension check

One question to the user to check understanding of principles actually applied in this task: architecture / code / mechanism / library / platform behavior. The goal is interview prep for Senior Android / KMP, not a merge-readiness audit.

**Source of the question:**
- First open the [interview prep checklist](../../../docs/interview-prep-checklist.md) and find an uncompleted item (`- [ ]`) that thematically matches what changed in the PR. If there is a match — ask based on it.
- If no uncompleted item matches the context of the task — formulate your own question based on the actual implementation (what was specifically done in the PR and why).

**Format:**
- Exactly one question per run, not a series. Not "tell me about X and Y and Z".
- Open-ended question, not yes/no. A detailed answer is required.

**After the user's answer:**
- Assess correctness: what is right, what is missing, what is imprecise or wrong. Without leniency and without aggression — like a technical interviewer giving honest feedback.
- If the answer was based on an item from the checklist — mark it as completed (`- [ ]` → `- [x]`) directly in `docs/interview-prep-checklist.md` via Edit. If the question was your own (not from the checklist) — add it to the "Additional questions from tasks" section at the end of the checklist as `- [x] <question>` via Edit.
- **Commit the checklist edit into the current PR branch and push, before the Step 8 merge.**

**This is not a stop-point based on the content of the answer** — a weak answer does not block the merge. The user decides themselves whether to proceed or explore the topic further. The stop-point is only the fact that the question was asked and an answer was received.

---

## Step 3 — Code review

Check the PR:

```bash
gh pr view <PR> --json reviews,comments --jq '{reviews: .reviews, comments: .comments}'
```

Make sure all review comments are resolved (resolved or replied to with a justification). The `/close-issue` invocation itself is the user's approval for the merge; a separate APPROVED review or a "lgtm" phrase is not required.

**The review must cover the final diff.** If commits landed after the most recent review pass, resolved comments on the earlier state are not coverage of what is about to merge — run a fresh review pass (`/code-review`, or the `implement` review wave) over the current diff before proceeding.

---

## Step 4 — Update documentation

### 4.1 Status in features/README.md

If the task implemented a feature from `docs/product/features/README.md` — update its status to `done`.
If this is an intermediate task (part of a feature) — update the status only if the feature is fully complete.

### 4.2 Affected documentation

Review the PR diff and determine: were any architectural or product decisions made during this task that diverge from the current documentation?

Files to check:
- `docs/product/` — if feature behavior, target audience, or stack changed
- `docs/engineering/` — if architectural principles, module layout, or DI rules changed
- `CLAUDE.md` — if build, testing processes, or project structure changed

If the documentation is out of date — update it. Small edits do immediately, large ones — create a separate issue.

**Apply the writing discipline.** Before writing or editing any long-lived artifact here, re-read [`long-lived-artifacts.md`](../../../docs/engineering/long-lived-artifacts.md) and apply it — do not write from memory.

**Doc-as-spec on first implementation of an architectural sketch.** If the PR is the first real implementation of a pattern that was a sketch in `docs/engineering/` (marker: "skeleton lands in #N" or code examples without a working implementation) — the doc must be updated in the same PR. Otherwise, the next implementor will follow an outdated example.

**New runtime flag — the entry-point doc must mention it.** If the PR introduces a new runtime flag (env var, JVM system property, CLI option, build flag) that affects observable application behavior — the README or the corresponding entry-point section must mention it with at least one line and a link to the engineering doc. Engineering doc as the only documentation location does not count as coverage: a contributor / user looks in the README, not in `docs/engineering/`.

**New rule in a live document — audit actual code.** If the PR adds or extends a policy or rule in `docs/engineering/` (sensitive-data policy, naming convention, layering rule, etc.) — run it against the code actually touched in the fresh PR and make sure the diff does not violate the just-introduced rule. Otherwise the doc will immediately diverge from reality, or the rule will silently create invisible violations.

---

## Step 5 — Record engineering decisions made along the way

Review the issue, the conversation with the user, and comments in the PR: were any architectural / technical / process decisions made that are **not recorded** in `docs/engineering/` or `CLAUDE.md`?

Examples of such decisions:
- Choice of library, technology, or specific pattern
- Technical trade-offs — what was accepted and what was deferred
- Organizational principles (modules, layers, naming)
- Process conventions (branching, artifact format, what gets its own issue)

If such a decision exists and is not recorded anywhere — **before merging**, record it. A small decision — as a short entry in an existing doc. A large one — as a separate ADR or a new DOCS issue if the volume doesn't fit in the current PR.

Do not let a decision live only in the chat: sessions are lost, and the next contributor (or yourself a month later) won't see the history and will reopen the same question.

**Apply the writing discipline.** Before writing or editing any long-lived artifact while recording a decision (doc, ADR, KDoc, `.claude/**`), re-read [`long-lived-artifacts.md`](../../../docs/engineering/long-lived-artifacts.md) and apply it — do not write from memory.

---

## Step 6 — Post-factum size label

**Reconcile the `size:*` label against the actual cost to the user.** 
Size means the user's effort — not diff size, and not agent wall-clock: time spent waiting on an agent is free to the user and never counts toward size. 

Two axes carry the cost. 
**(1) Review rounds — the primary signal:** the count of ROOT (top-level) review findings and the number of rounds they cluster into (gaps > 60 min). 
**(2) Depth of engagement:** how far the user had to dive into the task — decisions they had to make, explanations they had to read, implementation directions they had to choose. 

**Combine by taking the heavier of the two axes, not the average — a task is as big as its costliest axis.** The bands calibrate only the review-rounds axis: read it against the current per-size means in [`.claude/sizing-bands.json`](../../sizing-bands.json). The engagement axis has no separate bands — place it on the same S/M/L ladder by analogy ("this cost about as much as a typical M") and let it raise the size above what the comment count alone implies, which is what happens when an agent absorbs the review and few comments surface. Take the larger of the two, then update via `gh issue edit <N> --remove-label size:S --add-label size:L`. 

Don't size by lines changed or commit count — a large but mechanical diff stays small. The label must reflect actual effort, otherwise the analytics and the `progress` task cost drift.

Don't treat this as "a poor estimate" — scope often grows along the way due to additions by the user or external conditions. Just record the fact.

---

## Step 7 — Known issues & next tasks (pre-merge gate)

Before merging — while the PR can still absorb a fix — surface every loose end. Sources: the issue's "Consequences" / "Out of scope", TODO/FIXME in the diff, anything deferred or scoped out during the work, and any cheap fix you spotted in a file you already touched.

State **each** item as: **"\<known problem / unfinished item\>. Can do now because … / Can't do now because …"** — your honest read of whether it belongs in this PR, per [`scope-discipline.md`](../../../docs/engineering/scope-discipline.md). Then ask the user **"What do you disagree with?"** and **stop**.

Hard stop-point: the user redirects do-now-vs-defer here, while the merge is still reversible — not after, when a cheap in-file fix can no longer ride along.

- "Do now" items → implement in this PR (back through the inner loop if non-trivial), re-run the relevant review/runtime checks, then merge.
- Confirmed defers → file via the `create-issue` skill (here, or right after merge).
- No loose ends — say so explicitly and proceed.

---

## Step 8 — Merge

```bash
gh pr merge <PR> --squash --delete-branch
```

Use squash unless otherwise specified. After merging, confirm the PR actually merged: `gh pr merge` can print a local-sync error (e.g. "Could not read from remote", "not possible to fast-forward") even when the server-side merge succeeded — leaving the worktree switched to `main` with pre-PR file content, which looks like a failed merge or lost work. Verify via `gh pr view <PR> --json state,mergedAt` (`state == MERGED`) before treating any local CLI error as failure; then confirm the branch is deleted.

---

## Step 9 — Retro

Were there any systemic signals during the task? Triggers for `/retro`:

- bugfix (always — a bug = the system let it through);
- user pointed to friction with a system component (guide, skill, command, template);
- documentation diverged from reality (outdated example, incorrect 3rd-party claim);
- a class of review comments that could have been caught by tooling / a rule / a hook;
- something went unexpectedly well — worth recording the mechanism for reproduction;
- the issue/spec wasn't sufficient to start without clarifications — a gap in `create-issue` or `_template.md`.

**Not a trigger:** agent made a mistake → review caught it → agent fixed it in the same PR (this is the system working as intended). Many iterations by themselves. Scope clarifications in chat.

If a trigger fired — propose `/retro` with the specific signal. Otherwise close with "I see no systemic signals".
