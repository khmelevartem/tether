---
name: implement
description: End-to-end implementation orchestrator for a GitHub issue. Plans, dispatches coder sub-agent, runs a fast review loop (coder ↔ reviewers) without user in the loop, runs smoke, and reports to user only at human-required gates (AC ambiguity, root cause uncertainty, smoke failure, final approval). Use when starting work on an issue.
---

# /implement — Issue-to-PR orchestrator

You are the orchestrator for implementing a single GitHub issue. You do NOT write code or review code yourself. You dispatch sub-agents and decide when to escalate to the user.

**Goal:** remove the user from the inner `code → review → fix → review` cycle. The user is consulted at human-required gates only.

## Input

Issue number `<N>`.

## Gate semantics — when to stop and ask the user

You MUST stop and ask the user in these cases (and only these):
- **G1. Spec or AC ambiguity** — issue's DoD is missing/stub, feature spec missing for FEATURE type, blocking open questions in spec.
- **G2. BUGFIX root cause** — for BUGFIX, the root cause is not confirmed by reproduction or experiment (per `/work-on-issue` step 2).
- **G3. Plan ambiguity** — plan conflicts with loaded engineering guides and you have no clean way to resolve.
- **G4. Smoke red/yellow** — smoke verdict is not 🟢 after the inner loop.
- **G5. Final approval** — after the inner loop converges to APPROVE and smoke is green, present the result for the user's manual verification before merge.

Everything else — implementation details, reviewer findings, fix iterations — you handle internally without the user.

## Step 1 — Read issue + load guides

```bash
gh issue view <N> --json title,body,labels,comments
```

Classify PR type. For FEATURE, look up `docs/product/features/README.md` for spec. Read it.

Apply Gate G1 and G2 if applicable. If gated → present to user, stop.

Load relevant engineering guides per `/work-on-issue` step 3 mapping.

## Step 2 — Plan

Use the built-in `Plan` agent (or `general-purpose` if plan unavailable) to produce a short implementation plan: phases, files to touch, validation strategy. For large issues, split into independent tracks (waves) like the video pattern.

Apply Gate G3 if the plan conflicts with guides → present to user, stop. Otherwise, accept and continue.

**Worktree setup.** If you are not already in `.claude/worktrees/<branch>/`:

```bash
git worktree add .claude/worktrees/feature-<N>-<short-slug> -b feature/<N>-<short-slug> main
cd .claude/worktrees/feature-<N>-<short-slug>
```

All subsequent work happens in the worktree.

## Step 3 — Inner loop: coder ↔ fast reviewers

Per track (or sequentially if single track):

**Iteration:**

1. Dispatch `coder` agent with the plan slice. Wait for completion.
2. Once `coder` reports green tests, dispatch a **fast reviewer wave** — a subset of review agents in parallel:
   - `review-dod` (always)
   - `review-correctness` (always unless DOCS/REFACTOR)
   - `review-guides` (always)
   - `review-tests` (always unless DOCS/INFRA)
   - `review-platform` (if diff touches platform source sets)
   Skip `review-reuse` and `review-adversarial` in the inner loop — they belong to the final review only.
3. If every reviewer says `APPROVE` and zero `[REQUIRED]` items → track done, go to Step 4.
4. Else → aggregate `[REQUIRED]` findings, dispatch `coder` again with the findings as input:

> Previous review found these issues that block the PR. Address each. Do not change anything outside their scope.
>
> <list of [REQUIRED] findings with file:line>

   Go back to step 2.

**Iteration limit:** 4 inner iterations per track. If not converged after 4 — escalate to user with the remaining findings; this signals a plan or scope problem the loop cannot fix.

## Step 4 — Full review

After all tracks converge, run the full `/code-review` skill on the local diff (not yet a PR — review the working tree against the issue). Apply any `[REQUIRED]` findings via `coder`. Re-run until full review approves or iteration cap hit.

## Step 5 — Smoke

Run `/smoke-test` blocks relevant to the diff (per `/work-on-issue` step 7 heuristics). Record the verdict.

Apply Gate G4: if 🟡/🔴 → present to user, stop.

## Step 6 — Commit + push + PR

Only after Step 5 is 🟢:

```bash
git add <relevant files>
git commit -m "#<N>: <message>"
git push -u origin feature/<N>-<short-slug>
gh pr create --title "<title>" --body "<...>"
```

PR body must include: AC verdict (DONE checklist), `## Dependency check` (if new deps), smoke verdict.

## Step 7 — Gate G5: hand off

Report to user:
- PR URL
- AC: all `[DONE]` (from review-dod)
- Smoke: 🟢 with list of blocks run
- Any `[UNVERIFIABLE]` items the reviewers flagged as questions for the author — needs the user's call
- Next step: manual verification, then `/close-issue <N>`

## Notes

- This orchestrator does NOT call `/close-issue` automatically. Merge is always a user decision.
- If at any iteration `coder` reports an open question (not a fixable finding — e.g., "the issue says X but the existing pattern is Y, which to follow?") — escalate to user immediately. The coder cannot decide architectural questions.
- Token discipline: every sub-agent runs in its own context. Your main thread holds only the plan, the per-iteration finding summaries, and the user-gate decisions. If your context exceeds 50% — pause and summarize before continuing.
- This skill is for one issue at a time. Multiple parallel issues = multiple invocations on multiple worktrees.
