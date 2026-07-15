---
name: choose-issue
description: Quick read-only look at the current `docs/sprints/sprint-NN.md` — pull issue numbers from the composition table + merge order, batch-check OPEN/PR status via `gh`, query `blocked_by` only for 🟢 items, and propose 1–3 candidates to start now with one-sentence justifications. No Gradle, no edits. Use when the user says "what to pick next", "next task from sprint", "что взять из спринта", "следующая задача", or invokes `/choose-issue`.
---

Quick look: what to pick from the current sprint right now. Read-only and `gh` only, no Gradle.

## 1. Sprint

Take `docs/sprints/sprint-NN.md` with the maximum NN. Read **only** the `## Состав` table and the `## Порядок мерджа` section. Extract issue numbers from the table; the merge order section carries the in-sprint chains (`#A → #B`, `||` for parallel branches). Skip the H1 subtitle and the `**Направления:**` line — they are decorative.

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

For each `<N>` still heading to 🟢 (open, no PR), also check for an active local worktree — either signal marks it 🟡 (another session already holds the issue):
```bash
git worktree list --porcelain | grep '^branch ' | sed 's|^branch refs/heads/||' | while read -r b; do
  n="$(printf '%s' "$b" | grep -oE '^[0-9]+')"
  if   [ "$n" = "<N>" ]; then echo "🟡 $b"                                                   # primary: branch renamed to <N>-slug
  elif [ -z "$n" ] && git log "$b" --oneline | grep -qE "^[a-f0-9]+ #<N>:"; then echo "🟡 $b" # fallback: pre-rename branch, #<N>: commit prefix
  fi
done
```

Blind spots — the worktree signal cannot see: uncommitted/unpushed work (invisible to both signals); other-machine sessions (not in local `git worktree list`); a stray worktree under `.claude/worktrees/` whose git-dir is not under `.git/worktrees/` (not real in-progress evidence — ignore it).

Marking:
- ✅ closed
- 🟡 open + open PR, or active local worktree/branch (see the worktree signal above)
- 🟢 open, no PR, no active worktree
- 🔴 blocked (if step 3 found an open blocker)

## 3. Blockers — only for 🟢

For each 🟢:
```bash
gh api repos/khmelevartem/tether/issues/<N>/dependencies/blocked_by --jq '.[] | "\(.number) \(.state)"'
```

If there is an open blocker → 🔴. Also account for the in-sprint chains from the `## Порядок мерджа` section.

## 4. Output (compact)

```
Sprint NN
✅ #a #b   🟡 #c   🟢 #d #e   🔴 #f (blocked by #g)

Pick now:
1. #N — <title>. Why: <one sentence>.
2. ...
```

1–3 items maximum, only from 🟢. Selection criteria (in descending order):
1. Unblocks the longest tail (per merge-order chains or external `blocked_by`).
2. Does not conflict with what is already 🟡 — different `Тип` from the composition table, or different files (quick `gh pr view <PR> --json files` on the in-flight 🟡 PR).
3. Smaller size → faster.

If 🟢 is empty — say so, and propose: finish 🟡, unblock 🔴, or `/grooming` for a new sprint.

## Do not

- Do not edit the sprint doc (that's `/grooming` step 0).
- Do not run Gradle / tests / smoke.
- Do not do gap analysis, do not create issues.
- Do not invoke `/implement` — only propose the command.
