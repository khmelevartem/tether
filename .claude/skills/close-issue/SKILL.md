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
- **Commit the checklist edit into the current PR branch and push, before Step 6 merge.**

**This is not a stop-point based on the content of the answer** — a weak answer does not block the merge. The user decides themselves whether to proceed or explore the topic further. The stop-point is only the fact that the question was asked and an answer was received.

---

## Step 3 — Code review

Check the PR:

```bash
gh pr view <PR> --json reviews,comments --jq '{reviews: .reviews, comments: .comments}'
```

Make sure all review comments are resolved (resolved or replied to with a justification). The `/close-issue` invocation itself is the user's approval for the merge; a separate APPROVED review or a "lgtm" phrase is not required.

**The review must cover the final diff, not a stale one.** If commits landed after the most recent review pass — merges, fix iterations, late additions — resolved comments on the earlier state are not coverage of what is about to merge. Run a fresh review pass (`/code-review`, or the `implement` review wave) over the current diff before proceeding. Test-discipline regressions that slip in late commits — a bugfix that ends up without a failing-pre/passing-post test, tests deleted or weakened during a refactor — are caught by that pass, not by re-reading old threads.

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

**Doc-as-spec on first implementation of an architectural sketch.** If the PR is the first real implementation of a pattern that was a sketch in `docs/engineering/` (marker: "skeleton lands in #N" or code examples without a working implementation) — the doc must be updated in the same PR. Otherwise, the next implementor will follow an outdated example.

**New runtime flag — the entry-point doc must mention it.** If the PR introduces a new runtime flag (env var, JVM system property, CLI option, build flag) that affects observable application behavior — the README or the corresponding entry-point section must mention it with at least one line and a link to the engineering doc. Engineering doc as the only documentation location does not count as coverage: a contributor / user looks in the README, not in `docs/engineering/`.

**New rule in a live document — audit actual code.** If the PR adds or extends a policy or rule in `docs/engineering/` (sensitive-data policy, naming convention, layering rule, etc.) — run it against the code actually touched in the fresh PR and make sure the diff does not violate the just-introduced rule. Otherwise the doc will immediately diverge from reality, or the rule will silently create invisible violations.

**Findings from manual / smoke validation — fold the durable ones into `docs/knowledge/`.** Manual testing and the smoke run often surface platform quirks or behavioural gotchas that are not the task's subject but will bite the next contributor (a discovered edge of the fix, a confusing-but-correct observation, a workaround). A finding that outlives this task belongs in `docs/knowledge/` (extend the relevant note or add one), not only in the chat or the PR thread. If it is a defect rather than a quirk, file an issue instead.

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

---

## Step 6 — Merge

**Before merging: compare the `size:*` label with the actual volume of work.** If the task was originally `size:S` but turned out to be `size:M` (or vice versa) — update the label via `gh issue edit <N> --remove-label size:S --add-label size:M`. The label should reflect how it actually turned out, not the initial estimate — otherwise future analytics on volumes will be inaccurate.

Don't treat this as "a poor estimate" — scope often grows along the way due to additions by the user or external conditions. Just record the fact.

```bash
gh pr merge <PR> --squash --delete-branch
```

Use squash unless otherwise specified. After merging, verify that the PR is closed and the branch is deleted.

---

## Step 7 — Next tasks

Look in the issue for a "Consequences" section and in the PR — are there TODOs, unresolved questions, things moved out of scope.

If there are explicit next steps — offer to create issues (use the `create-issue` skill).
If there is nothing — say so explicitly.

---

## Step 8 — Retro

Were there any systemic signals during the task? Triggers for `/retro`:

- bugfix (always — a bug = the system let it through);
- user pointed to friction with a system component (guide, skill, command, template);
- documentation diverged from reality (outdated example, incorrect 3rd-party claim);
- a class of review comments that could have been caught by tooling / a rule / a hook;
- something went unexpectedly well — worth recording the mechanism for reproduction;
- the issue/spec wasn't sufficient to start without clarifications — a gap in `create-issue` or `_template.md`.

**Not a trigger:** agent made a mistake → review caught it → agent fixed it in the same PR (this is the system working as intended). Many iterations by themselves. Scope clarifications in chat.

If a trigger fired — propose `/retro` with the specific signal. Otherwise close with "I see no systemic signals".
