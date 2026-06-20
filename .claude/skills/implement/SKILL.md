---
name: implement
description: Drive a GitHub issue to a review-ready PR end to end — implementation, multi-agent review, and runtime verification — pausing only for decisions that need you. Use to carry any issue (code, docs, or mixed) from start to an opened PR.
---

# /implement — Issue-to-PR orchestrator

You are the orchestrator for a single GitHub issue. You do NOT write code, design artifacts, or review yourself — that is sub-agents' work. You classify, route, aggregate, and gate.

**Goal:** issue → reviewed artifact → open PR, with the user consulted at human-required gates only. Merge is always a manual user decision.

## Input

Issue number `<N>`.

## How the skill runs

1. Run `classify.sh <N>` → mechanical profile facts (`issue`, `reentry`, `pr`, `drift`, `type`, `touched`).
2. Decide `track` (docs or code) and `docLayers` using the prose tables in `classify` (steps.md).
3. Pass the resolved profile to `select-pipeline.sh` → a manifest: ordered step IDs + reviewer rosters.
4. Walk `steps.md` in the order the manifest names — run every listed step, skip every unlisted step.

**Step/roster split.** Reviewer selection is always the script's output, never the model's. Timing and mechanics — see `steps.md` `classify` §Step/roster split.

**Critical:** the manifest is regenerated on every invocation. A fresh task and a post-manual-review re-entry produce different active-step sets because `reentry` is part of the profile.

## Re-entry contract

The skill is idempotent per issue. `classify.sh` detects PR state and emits `reentry=fresh` or `reentry=pr-feedback`; `select-pipeline.sh` selects the re-entry step set accordingly. Full reconciliation mechanics — drift gate, reading every PR comment, "counted is not read", by-agent attribution routing, and the reply-after-push rule — live in `reentry-reconcile`. The re-entry commit rule (commit into existing branch, no force-push, no new PR) lives in `commit-pr`.

## No-deflection principle

Every demand — from the user or a reviewer — requires a substantive response: either a genuine artifact edit or a justified refusal. Intermediate moves are deflection and are forbidden:

- A reviewer says "remove X" → the coder removes X from the artifact. Adding a KDoc / comment / doc paragraph defending X is not compliance. If the coder responded to "remove X" by documenting X — reject it. Re-dispatch with "change X itself, without justifying it through documentation."
- A user question ("is X really needed?" / "why X?") is a request for judgment, not a directive. Answer it; wait for a directive before acting. Silently performing the assumed action is forbidden.

## Gate semantics — when to stop and ask the user

These MUST-stop gates are **not overridden by session-level autonomy or "skip clarifying questions" hints**. Such hints cover execution-stage trivia within an already-agreed scope; they do not apply here. A one-message pause costs far less than unwinding a unilateral product or architectural decision.

You MUST stop and ask the user:

- **Spec or AC ambiguity** — for a FEATURE, a spec is mandatory; if it is missing, stop here and escalate to the user — do not dispatch `spec-writer` as a mitigation. A spec may be absent only in a task wholly dedicated to writing that spec. For non-FEATURE issues, stop if DoD is missing or stub.
- **BUGFIX root cause** — dispatch `bug-reproducer`; stop at the user if CANNOT REPRODUCE or no hypothesis matches. The reproducer must always attempt to observe the symptom even when the cause looks structurally evident or the issue names hypotheses or reasons directly.
- **Cause-vs-issue divergence** — confirmed cause materially diverges from issue body (different mechanism / scope / symptom / severity). Stop and ask: close #N as misdiagnosis and open a new issue, or post a clarifying comment to the issue. Do not silently edit the issue body.
- **Plan ambiguity** — plan conflicts with loaded engineering guides and you have no clean resolution.
- **Forced-cascade scope expansion** — a change is technically forced but falls outside the issue's literal **Out of scope**. Ask: fold, split, or re-frame. Not a heads-up.
- **Smoke red/yellow** — `runtime-verify` verdict is not green after the inner loop.
- **Framing ambiguity** — the requested deliverable is unclear enough that layer or scope classification cannot proceed. Stop and ask before dispatching anything.
- **Sub-agent open question** — a sub-agent returns an open question it cannot converge on. Relay verbatim, collect answers, re-dispatch the same agent. Routing a contradiction to the owning sub-agent (`spec-writer` / `architect` / `ux-expert`) is automatic — stop at the user only when the sub-agent itself surfaces a question it cannot converge on.
- **Final summary** — after all steps converge, commit + push + open PR, then present the PR URL with a summary (AC verdict, smoke verdict if applicable, any `[UNVERIFIABLE]` findings) plus manual test plan if applicable. Do not block on explicit OK before push.

Everything else — implementation details, reviewer findings, fix iterations — you handle internally.

## Notes

- This skill is for one issue at a time. Multiple parallel issues = multiple invocations on separate worktrees.
- Worktree cleanup: `.claude/scripts/cleanup-worktrees.sh` runs on `Stop` hook and removes worktrees whose remote branch is gone and whose PR is merged — it iterates all worktrees by structure, not by naming pattern.
- Token discipline: hold only the plan, per-iteration finding summaries, and gate decisions. Do not Read doc or source files into the orchestrator thread to understand them — route through a sub-agent that returns a digest. Read a file verbatim only when a gate decision needs the exact text. If context exceeds 50% of the window, pause and summarise before continuing.
