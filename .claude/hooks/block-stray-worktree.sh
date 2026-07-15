#!/usr/bin/env bash
# PreToolUse/Bash hook: deny commands run from a stray (unregistered) worktree.
# A dir under .claude/worktrees/ that was never `git worktree add`-ed nests inside
# the main working tree, so git walks up and resolves to the MAIN checkout — any
# git op there (branch rename, writes to the git dir) silently mutates main. Deny
# before that happens; the fix is to rebuild the worktree. Sibling of
# block-cross-worktree-writes.sh (that one guards Edit/Write paths; this one guards
# the git-dir fallthrough that Bash git ops would hit).

set -euo pipefail

[ "${TETHER_SKIP_WORKTREE_HOOK:-}" = "1" ] && exit 0

input=$(cat)

# Cheap short-circuit: only worktree-scoped calls carry the marker in cwd.
case "$input" in
  *'/.claude/worktrees/'*) ;;
  *) exit 0 ;;
esac

printf '%s' "$input" | python3 -c '
import json, subprocess, sys

try:
    data = json.loads(sys.stdin.read())
except json.JSONDecodeError:
    sys.exit(0)

if data.get("tool_name") != "Bash":
    sys.exit(0)

cwd = data.get("cwd", "")
marker = "/.claude/worktrees/"
if marker not in cwd:
    sys.exit(0)

try:
    git_dir = subprocess.run(
        ["git", "-C", cwd, "rev-parse", "--absolute-git-dir"],
        capture_output=True, text=True, timeout=5,
    ).stdout.strip()
except Exception:
    git_dir = ""

# A registered linked worktree resolves its git dir under .git/worktrees/. Anything
# else (the main .git, or unresolved) means the worktree is stray — git ops here
# would fall through to the main checkout.
if "/.git/worktrees/" in git_dir:
    sys.exit(0)

repo_root = cwd.rsplit(marker, 1)[0]
wt_name = cwd.rsplit(marker, 1)[1].split("/")[0]
git_dir_display = git_dir or "unresolved"
reason = (
    "Blocked: stray worktree. This cwd sits under .claude/worktrees/ but git "
    f"resolves to the main checkout (git dir: {git_dir_display}), so any "
    "git op here would corrupt main. Rebuild the worktree first:\n"
    f"  git worktree add {repo_root}{marker}{wt_name} <branch>\n"
    "For a deliberate main-checkout op, set TETHER_SKIP_WORKTREE_HOOK=1."
)
print(json.dumps({
    "hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "permissionDecision": "deny",
        "permissionDecisionReason": reason,
    }
}))
'
