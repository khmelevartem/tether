# Claude Code harness: worktrees, branches, and transcripts

Observed behaviour of the Claude Code harness, which is external and undocumented at this level — treat the specifics as current, not contractual.

## Behaviour

The harness creates one git worktree per session under `.claude/worktrees/<random-slug>/`, on a branch of the same slug, and starts the session inside it. No repo-side hook fires at creation time — hooks run only once the worktree already exists.

Session identity is an opaque id, keyed on neither the worktree path nor the branch.

The transcript lives in a directory derived from the worktree's absolute path, under `~/.claude/projects/`. It is keyed on the directory path, never on the branch.

## What follows

- **Renaming the branch mid-session is safe.** It has no path side effects, and the transcript is path-keyed, so it is unaffected. Every repo hook keys on live git state or the literal `/.claude/worktrees/` path marker — never on a stored name — so a rename is transparent to them. This is what lets `/implement` give the branch the issue number.
- **Moving or renaming the worktree directory mid-session is unsafe.** The live session's working directory points at the old path, which then vanishes, and the path-derived transcript is orphaned. It is safe only at a session boundary, when no live session is attached.
- **The name cannot be set at creation time from the repo.** The harness names the worktree before any repo hook runs, so the only lever is an after-the-fact rename.
- **Removing a worktree mid-session relocates its transcript** to the origin project directory rather than deleting it; retention follows the harness cleanup-period setting.

## See also

- [`.claude/skills/implement/scripts/ensure-branch.sh`](../../.claude/skills/implement/scripts/ensure-branch.sh) — the after-the-fact branch rename
- [`.claude/scripts/cleanup-worktrees.sh`](../../.claude/scripts/cleanup-worktrees.sh)
- [#482](https://github.com/khmelevartem/tether/issues/482)
