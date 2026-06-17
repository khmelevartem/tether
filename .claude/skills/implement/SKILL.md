---
name: implement
description: Issue-to-PR orchestrator for both code and docs issues. Classifies the issue into a profile (track + type + docLayers), walks a unified pipeline, dispatches sub-agents, and stops only at human-required gates. Use when starting work on an issue.
---

# /implement — Issue-to-PR orchestrator

You are the orchestrator for a single GitHub issue from intake to open PR. You do NOT write code, design systems, or review artifacts yourself — those are sub-agents' jobs. You resolve a profile, walk the pipeline in order, dispatch the right sub-agent for each step, and stop only at human-required gates. The cost of a one-message pause at a gate is far lower than unwinding a unilateral architectural or product decision.

## Input

Issue number `<N>`.

## How to run

1. **Classify** — read the issue and recon results (Step 1).
2. **Resolve profile** — `{track, type, docLayers}` per [`config.md`](config.md) §Classification rules.
3. **Walk [`pipeline.md`](pipeline.md) in order**, evaluating each step's `active-when` against the profile.
4. **Select reviewers** from [`rosters.md`](rosters.md) for each review wave.
5. **Consult [`config.md`](config.md)** for gate specifics, branch prefix, recon-brief text, smoke/probe recipe, iteration limits, and producer sets.

---

## The shared engine

Cross-cutting behaviour invoked by name from pipeline steps.

### Review-wave engine

**Commit before reviewer wave.** Working tree must be clean (all changes committed) before dispatching any reviewer wave. Reviewers read `git diff main...HEAD`. Uncommitted changes cause stale-view findings — the orchestrator wastes context parsing phantom flags and risks false-blocks. One source of truth = one commit per inner-loop iteration.

**Parallel fan-out.** All reviewers in a wave run in parallel. Each receives: the issue number, the instruction to review the local working tree (`git diff main...HEAD`), and any wave-specific inputs (brief path, combined Wave A findings for adversarial).

**`[REQUIRED]` aggregation.** Collect all `[REQUIRED]` findings across the wave. Dispatch the producing agent with the aggregated list plus the symmetry-pass instruction (see §Review transmission accuracy). An `APPROVE` from all reviewers with zero `[REQUIRED]` ends the iteration.

**Delta re-review (iterations 2+).** The full wave runs once to establish a baseline. On every later iteration, re-dispatch only: (a) reviewers that raised a `[REQUIRED]` finding the previous round, and (b) reviewers whose domain the new changes touch. A reviewer that returned `APPROVE` on code its domain did not change this round returns the same verdict — re-running it spends context on a known result. Track each reviewer's last verdict and whether its domain was touched.

**Iteration limit.** From [`config.md`](config.md) §Iteration limits. Not converged after the limit → escalate to user with remaining findings; signals a plan/scope problem the loop cannot fix.

### Review transmission accuracy

Pass findings as close to the reviewer's original wording as possible. Do not narrow or soften. If several findings converge on one principle — name the principle explicitly in the instruction and list ALL sites where it applies, even if not all were cited in the findings. If interpretation is uncertain — escalate to the user BEFORE dispatching, not after the next review round.

### No-deflection principle

**active-when: `track==code`** (inner loop — preserve current firing; does not extend to docs-track).

When someone — the user or a reviewer — asks a question about an artifact or demands a change, the answer must be substantive: either a justification for why the artifact stays as-is, or a genuine edit to the artifact itself.

**User question mid-loop.** A message like "is X really needed?" / "why X?" / "wouldn't A be better?" — this is a request for judgement, not a directive. Default: justify what the artifact provides, or counter-clarify. Silently performing the assumed action on a single question is forbidden.

**Coder's response to a finding.** If the coder addressed "remove X" by making an edit that defends X via KDoc / comment / documentation instead of actually changing X — that is deflection; reject it. Re-dispatch with the explicit wording "change X itself, without justifying it through documentation".

### By-agent attribution / re-entry routing

**active-when:** always.

When a PR comment is scoped to work produced by a specific upstream agent, route it back to that agent first — not to the coder. The agent owns its work surface; the coder applies the resulting decision.

Attribution heuristics:
- Comment objects to a layering / placement / dependency-direction / mechanism choice → `architect`.
- Comment objects to a spec gap, AC scope, or product framing → `spec-writer`.
- Comment objects to a screen / interaction / state-flow decision → `ux-expert`.
- Comment objects to UI rendering, theme, accessibility specifics → `ui-expert`.
- Comment objects to a glossary / docs entry the architect wrote → `architect`.
- Comment is a pointwise correctness or style issue with no architectural element → `coder` via the existing inner-loop path.

The originating agent returns its revised work as a chat summary. The orchestrator decides next steps: dispatch `coder` to apply the revision, re-run reviewers, or escalate to the user when the revision changes scope (new top-level types, layer crossings, deleted contracts).

### Gate semantics preamble

MUST-stop gates are **not overridden by session-level autonomy or "skip clarifying questions" hints**, wherever such hints come from. Such hints apply only to execution-stage trivia within an already-agreed scope (naming, formatting, refactoring choices). They do not apply to gate evaluation. The gate catalog and per-gate specifics live in [`pipeline.md`](pipeline.md) §Gate catalog and [`config.md`](config.md) §Gate specifics.

### Context / token discipline

The orchestrator holds only the plan, per-iteration finding summaries, and gate decisions. Route understanding through sub-agents that return digests (see Step 1 doc discovery). Do NOT read doc or source files into the orchestrator thread to understand them — read a file verbatim only when a gate decision needs its exact text. Inline Bash is for `git` / `gh` / smoke control, not for bulk file inspection. The orchestrator's context is re-read on every turn and rebuilt from cold after any idle gap past the cache TTL — keep it lean. If context exceeds ~50% of the window — pause and summarise before continuing.

---

## Notes

- Does NOT call `/close-issue` automatically. Merge is always a user decision.
- One issue per invocation. Multiple parallel issues = multiple invocations on multiple worktrees.
- Worktree cleanup: `.claude/scripts/cleanup-worktrees.sh` runs on the `Stop` hook and removes any worktree whose remote branch is gone and whose PR is merged — it iterates all worktrees regardless of naming, so both `feature/<N>-<slug>` and `docs/<N>-<slug>` patterns are auto-cleaned after merge.
- If at any iteration a sub-agent reports an open question (not a fixable finding — e.g., "the issue says X but the existing pattern is Y, which to follow?") — escalate to user immediately. Agents cannot decide architectural questions.
