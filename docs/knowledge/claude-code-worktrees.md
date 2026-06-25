# Claude Code harness: worktrees, branches, and transcripts

## Behaviour

The harness creates one git worktree per session under `.claude/worktrees/<random-slug>/`, on a branch of the same slug, and starts the session inside it. No repo-side hook fires at creation time — `SessionStart` runs only after the worktree already exists.

Session identity is a UUID in `CLAUDE_CODE_SESSION_ID`, not the path or branch; no environment variable holds the worktree path or branch name.

The transcript directory is derived one-to-one from the worktree's absolute path, with `/` and `.` replaced by `-`: `~/.claude/projects/<slugified-abs-path>/<session-id>.jsonl`. It is keyed on the directory path, never on the branch.

## What follows

- **Renaming the branch mid-session is safe.** `git branch -m` has no path side effects, and the transcript is path-keyed, so it is unaffected. Every repo hook keys on live git state or the literal `/.claude/worktrees/` path marker — never on a stored name — so a rename is transparent to them. `.claude/skills/implement/scripts/ensure-branch.sh` relies on this to make the branch carry the issue number.
- **Moving or renaming the worktree directory mid-session is unsafe.** The live session's working directory points at the old path, which then vanishes, and the path-derived transcript is orphaned. It is safe only at a session boundary, when no live session is attached.
- **The name cannot be set at creation time from the repo.** The harness names the worktree before any repo hook runs, so the only lever is an after-the-fact rename (e.g. early in `/implement`).
- **Removing a worktree mid-session relocates its transcript** to the origin project directory rather than deleting it; retention is governed by `cleanupPeriodDays`.

## See also

- [`.claude/skills/implement/scripts/ensure-branch.sh`](../../.claude/skills/implement/scripts/ensure-branch.sh)
- [`.claude/scripts/cleanup-worktrees.sh`](../../.claude/scripts/cleanup-worktrees.sh)
- [#482](https://github.com/khmelevartem/tether/issues/482)
