---
name: retro
description: Run a retrospective on a GitHub task (issue + PR) to identify systemic gaps and systemic successes, propose changes to CLAUDE.md / docs / skills / commands / hooks, and apply approved ones on a separate retro branch with PR title `retro from #<PR>: …`. Use when the user says "retro", "retrospective", "разбор по задаче", "look back at task N", or invokes `/retro`.
---

Run a retrospective on task <issue number> (and the associated PR, if any).

The goal is not a report for its own sake and not a catalog of pointwise mistakes, but **systemic improvements**: what in the prompts, commands, skills, documentation, or project structure needs to change so that future assignees can do quality work more easily. Every conclusion must end with an action.

**What the retro is NOT interested in.** Pointwise agent mistakes that the reviewer caught and that were fixed in the same PR — this is the system working as intended, not a failure. The retro is not about "the agent could have been more careful". The retro is about "how to make this class of mistakes unlikely regardless of attentiveness".

**What the retro IS interested in.** Systemic gaps (gaps in docs / skills / commands / prompts) and systemic successes (what went unexpectedly smoothly due to architecture / tool / pattern — and how to reproduce it in future tasks).

---

## Step 0 — Load the writing discipline

Every change a retro produces is a long-lived artifact. Read [`docs/engineering/long-lived-artifacts.md`](../../../docs/engineering/long-lived-artifacts.md) before drafting any wording and apply it through the analysis and proposed edits, not only at the Step 6 commit gate.

---

## Step 1 — Gather facts

Collect context:

```bash
gh issue view <N> --json title,body,comments
gh pr view <PR> --json title,body,commits,comments,reviews  # if known
git log --oneline <merge-commit> -1  # if already merged
```

Read the full history: issue, comments, review, conversation in the PR.

---

## Step 2 — Systemic analysis

Focus: **system → action**, not "agent → attentiveness".

**Search lens — weight signals before cataloguing them.** The first signal that surfaces is usually the most *visible* defect, not the most *valuable*. Hunt by:
- **Cost, not visibility.** Rank by the rework a gap caused — a decision built on the wrong axis and redone outranks a discrete thing review missed. Follow the longest detour in the task's history to its root.
- **Where the human was the safety net.** Every point where the user had to correct, re-explain, repeat a request, or run a manual audit is a systemic signal — the system leaned on the user where a doc / skill / prompt / check should have carried it. Recurrence — the same kind of correction needed more than once — raises the priority.
- **Wrong question, not wrong answer.** Look for framing/axis mismatches — the answer was locally fine but the question was on the wrong axis (mechanism asked when the user cared about the observable outcome). These hide because every step looks correct in isolation.
- **The whole arc.** Spread the scan across design → implement → review → feedback → close; one phase rarely holds all the signals.

**Systemic gaps:**
- A gap in `CLAUDE.md` / `docs/engineering/` / a skill / a template that made the mistake *programmatic*?
- Did the assignee / reviewer rely on something that turned out to be incorrect or outdated (doc, example, 3rd-party claim)?
- A class of review comments that can be caught by tooling / a hook / a rule — not by attention?
- What was missing in the issue/spec to start without clarifications?

**Systemic successes:**
- What went unexpectedly smoothly, and why?
- Is there a reproducible logic — to codify as a principle in `docs/engineering/`, a check in a skill, an item in a command template?
- A free multiplier (one action → multiple platforms/tasks)? By what mechanism, how to extend it to similar cases?

If there are neither gaps nor successes worth recording — close with "I see no systemic changes", don't invent action items for the sake of form.

---

## Step 3 — Bug analysis (only if the task is a bugfix)

Bugfix = the system let a bug through to main. Retro is mandatory. Systemically:

1. **How did the bug get into main?** Absence of a class of tests; a guide described a pattern that led to the bug; review didn't catch it — which specific check didn't work; an edge case was known but not recorded.
2. **Where is systemic protection needed?** A test for the class of bugs, validation, a check in `/code-review`, a hook on a pattern, an example/restriction in `CLAUDE.md` or the spec.

Simple protection — in the retro PR; larger — as a separate issue.

---

## Step 4 — What to optimize

Based on steps 2–3, identify specific systemic changes. Categories:

| Category | When to touch |
|----------|--------------|
| `.claude/skills/<name>/SKILL.md` | The skill (workflow procedure or orchestration) didn't cover the needed scenario, didn't prevent a class of errors, or lacked a needed step |
| `.claude/commands/*.md` | One of the remaining single-file templates (`merge`, `progress-boring`) gave unclear instructions or missed a scenario |
| `.claude/agents/<name>.md` | A sub-agent's brief is incomplete, allowed an out-of-scope decision, or missed a check |
| `CLAUDE.md` | A convention / process / project structure is not recorded — the agent had to guess |
| `docs/engineering/` | An architectural principle / pattern is recorded incorrectly, incompletely, or its examples diverge from reality |
| `docs/product/features/` | A feature spec is incomplete or missing where it would have been useful |
| `.claude/skills/create-issue` | Issues came out with insufficient context for direct implementation |
| Hook in `scripts/install-hooks.sh` | A class of errors is catchable automatically at the git operation level, not only by instruction |

For each potential change explicitly state **which systemic gap it addresses** — without this link the change becomes an addition "just in case".

---

## Step 5 — Formulate actions (without applying)

Based on the analysis, compile a list of proposed changes. **Do not apply them immediately** — explicit approval is needed first.

For each improvement specify:
- What exactly to change (file, section)
- Which systemic gap or systemic success it addresses (explicit link to a specific incident / effect from the task history)
- Size: "small" (fix in place) or "task" (separate issue)

Output the list and explicitly ask:

> Do you confirm these changes? If not — tell me what to adjust.

**Do not proceed to step 6 without an explicit "yes" or "ok" from the user.**

---

## Step 6 — Apply (only after approval)

**Make all changes on a separate branch from `main`, not directly on `main`.** Retro fixes are the same process as regular tasks: branch → commit → PR → review. Even if the changes are small and in `.claude/` — they go through a PR, so that a trace remains and rollback is possible.

For each confirmed improvement:

**If the change is small** (edit in a document, clarification in a command) — do it right now and show the diff.

**Long-lived-artifacts compliance check before commit.** Every prose edit retro produces lands in a long-lived artifact by construction. Before committing, audit every paragraph and bullet against [`docs/engineering/long-lived-artifacts.md`](../../../docs/engineering/long-lived-artifacts.md).

**If the change requires a separate task** — create an issue via the `create-issue` skill. The name should read as a useful increment to the workflow, for example:
- "Add check X to code-review.md"
- "Clarify scope of implement skill for case Y"

**PR title for retro fixes** must start with `retro from #<PR>: ...`, where `<PR>` is the number of the original PR the retro was run on. This gives an immediately visible link and simplifies search. Example: `retro from #70: clarify smoke step in close-issue command`.

**Retro outcome** — output as a list:
- ✅ Done immediately: [what exactly, which gap/success it addresses]
- 📋 Planned: [issue #N — what, which gap it addresses]
- 💡 Observation without action: [if any — why without action]
