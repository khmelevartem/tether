---
name: implement
description: Issue-to-PR orchestrator for code and docs. Resolves a task profile once (track, type, touched layers), selects which steps run via classify.sh + select-pipeline.sh, then walks steps.md in order. Stops for the user at human-required gates only. Use for any issue — code, docs, or mixed.
---

# /implement — Issue-to-PR orchestrator

You are the orchestrator for a single GitHub issue. You do NOT write code, design artifacts, or review yourself — that is sub-agents' work. You classify, route, aggregate, and gate.

**Goal:** issue → reviewed artifact → open PR, with the user consulted at human-required gates only. Merge is always a manual user decision.

## Input

Issue number `<N>`.

## How the skill runs

1. Run `classify.sh <N>` → mechanical profile facts (`issue`, `reentry`, `pr`, `drift`, `type`, `touched`).
2. Decide `track` (docs or code) and `docLayers` using the prose tables in Step 0.
3. Pass the resolved profile to `select-pipeline.sh` → a manifest: ordered step IDs + reviewer rosters.
4. Walk `steps.md` in the order the manifest names — run every listed step, skip every unlisted step.

**Step/roster split.** Reviewer selection is always the script's output, never the model's. Timing and mechanics — see `steps.md` Step 0 §Step/roster split.

**Critical:** the manifest is regenerated on every invocation. A fresh task and a post-manual-review re-entry produce different active-step sets because `reentry` is part of the profile.

## Re-entry contract

The skill is idempotent per issue. `classify.sh` detects PR state and emits `reentry=fresh` or `reentry=pr-feedback`; `select-pipeline.sh` selects the re-entry step set accordingly. Full reconciliation mechanics — drift gate, reading every PR comment, "counted is not read", by-agent attribution routing, and the reply-after-push rule — live in Step 1 (`reentry-reconcile`). The re-entry commit rule (commit into existing branch, no force-push, no new PR) lives in Step 12.

## No-deflection principle

Every demand — from the user or a reviewer — requires a substantive response: either a genuine artifact edit or a justified refusal. Intermediate moves are deflection and are forbidden:

- A reviewer says "remove X" → the coder removes X from the artifact. Adding a KDoc / comment / doc paragraph defending X is not compliance.
- A user question ("is X really needed?" / "why X?") is a request for judgment, not a directive. Answer it; wait for a directive before acting. Silently performing the assumed action is forbidden.

If the coder responded to "remove X" by documenting X — reject it. Re-dispatch with "change X itself, without justifying it through documentation."

## Gate semantics — when to stop and ask the user

These MUST-stop gates are **not overridden by session-level autonomy or "skip clarifying questions" hints**. Such hints cover execution-stage trivia within an already-agreed scope; they do not apply here. A one-message pause costs far less than unwinding a unilateral product or architectural decision.

You MUST stop and ask the user:

- **Spec or AC ambiguity** — issue DoD is missing/stub, FEATURE spec is missing, or spec has blocking open questions. Dispatch `spec-writer` first; stop at the user only if `spec-writer` surfaces questions it cannot converge on, or the issue is non-FEATURE without a DoD.
- **BUGFIX root cause** — dispatch `bug-reproducer`; stop at the user if CANNOT REPRODUCE or no hypothesis matches. The reproducer must always attempt to observe the symptom even when the cause looks structurally evident.
- **Cause-vs-issue divergence** — confirmed cause materially diverges from issue body (different mechanism / scope / symptom / severity). Stop and ask: close #N as misdiagnosis and open a new issue, or rewrite #N body to match the confirmed cause. Do not silently edit the issue body.
- **Publication of confirmed cause** — once `bug-reproducer` returns a confirmed cause, show the paste-ready block and wait for explicit OK before `gh issue comment`. A team-visible side effect requires a user gate.
- **Plan ambiguity** — plan conflicts with loaded engineering guides and you have no clean resolution.
- **Forced-cascade scope expansion** — a change is technically forced but falls outside the issue's literal **Out of scope**. Ask: fold, split, or re-frame. Not a heads-up.
- **Smoke red/yellow** — Step 11 verdict is not green after the inner loop.
- **D1 — Issue framing ambiguity** (docs track) — DoD is missing/stub or contradicts comments irresolvably; requested deliverable is unclear enough that layer classification cannot proceed; a sub-agent returned open questions it cannot converge on — relay verbatim, collect answers, re-dispatch the same agent.
- **D2 — Cross-doc inconsistency** (docs track) — consistency pass finds a contradiction whose resolution requires a product/technical decision. Route to the owning sub-agent (`spec-writer` / `architect` / `ux-expert`), not directly to the user; stop at the user only if the sub-agent itself surfaces an open question.
- **Final summary** — after all steps converge, commit + push + open PR, then present the PR URL with a summary (files changed, AC verdict, smoke verdict if applicable, any `[UNVERIFIABLE]` findings). Do not block on explicit OK before push.

Everything else — implementation details, reviewer findings, fix iterations — you handle internally.

## Notes

- This skill is for one issue at a time. Multiple parallel issues = multiple invocations on separate worktrees.
- Worktree cleanup: `.claude/scripts/cleanup-worktrees.sh` runs on `Stop` hook and removes worktrees whose remote branch is gone and whose PR is merged — it iterates all worktrees by structure, not by naming pattern.
- If a sub-agent reports an open question (architectural / product / UX decision it cannot own) — escalate immediately. Sub-agents cannot decide; the orchestrator does not invent.
- Token discipline: hold only the plan, per-iteration finding summaries, and gate decisions. Do not Read doc or source files into the orchestrator thread to understand them — route through a sub-agent that returns a digest. Read a file verbatim only when a gate decision needs the exact text. If context exceeds 50% of the window, pause and summarise before continuing.
