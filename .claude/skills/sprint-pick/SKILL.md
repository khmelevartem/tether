---
name: sprint-pick
description: Quick read-only look at the current `docs/sprints/sprint-NN.md` — pull issue numbers from Composition + Blocking chains, batch-check OPEN/PR status via `gh`, query `blocked_by` only for 🟢 items, and propose 1–3 candidates to start now with one-sentence justifications. No Gradle, no edits. Use when the user says "what to pick next", "next task from sprint", "что взять из спринта", "следующая задача", or invokes `/sprint-pick`.
---

Quick look: what to pick from the current sprint right now. Read-only and `gh` only, no Gradle.

## 1. Sprint

Take `docs/sprints/sprint-NN.md` with the maximum NN. Read **only** the sections "Sprint goal", "Composition", "Blocking chains". Extract issue numbers.

If the file doesn't exist — say so and propose `/grooming`. Stop.

## 2. Statuses in one batch

```bash
gh issue view <N1> <N2> ... --json number,state,title    # sequentially, without --jq, can be in one bash block via ;
```

Or shorter — for each `<N>` in parallel:
```bash
gh issue view <N> --json number,state,title
gh pr list --search "<N> in:title" --state open --json number,isDraft,mergeable
```

Group into one tool-call with `;` between commands. Do not call `dependencies/blocked_by` for all at once — only for those that are `OPEN` without an active PR (candidates for starting).

Marking:
- ✅ closed
- 🟡 open + open PR
- 🟢 open, no PR
- 🔴 blocked (if step 3 found an open blocker)

## 3. Blockers — only for 🟢

For each 🟢:
```bash
gh api repos/khmelevartem/tether/issues/<N>/dependencies/blocked_by --jq '.[] | "\(.number) \(.state)"'
```

If there is an open blocker → 🔴. Also account for the internal merge order from the "Blocking chains" section of the sprint doc.

## 4. Output (compact)

```
Sprint N — "<goal>"
✅ #a #b   🟡 #c   🟢 #d #e   🔴 #f (blocked by #g)

Pick now:
1. #N — <title>. Why: <one sentence>.
2. ...
```

1–3 items maximum, only from 🟢. Selection criteria (in descending order):
1. Unblocks the longest tail (mentioned in "Blocking chains outward" or blocking-links).
2. Does not conflict with what is already 🟡 (by layers from the sprint doc).
3. Smaller size → faster.

If 🟢 is empty — say so, and propose: finish 🟡, unblock 🔴, or `/grooming` for a new sprint.

## Do not

- Do not edit the sprint doc (that's `/grooming` step 0).
- Do not run Gradle / tests / smoke.
- Do not do gap analysis, do not create issues.
- Do not invoke `/implement` — only propose the command.
