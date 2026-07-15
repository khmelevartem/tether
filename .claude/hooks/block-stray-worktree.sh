#!/usr/bin/env bash
# PreToolUse/Bash hook: deny commands from a stray (unregistered) worktree, where
# git resolves to the main checkout so any git op would mutate main. Sibling of
# block-cross-worktree-writes.sh (Edit/Write paths) — this guards the Bash side.

set -euo pipefail

[ "${TETHER_SKIP_WORKTREE_HOOK:-}" = "1" ] && exit 0

input=$(cat)
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

# A registered worktree resolves under .git/worktrees/; anything else is stray.
if "/.git/worktrees/" in git_dir:
    sys.exit(0)

repo_root = cwd.rsplit(marker, 1)[0]
wt_name = cwd.rsplit(marker, 1)[1].split("/")[0]
git_dir_display = git_dir or "unresolved"
reason = (
    "Blocked: stray worktree — git resolves to the main checkout "
    f"({git_dir_display}), so git ops here would corrupt main. Rebuild it:\n"
    f"  git worktree add {repo_root}{marker}{wt_name} <branch>\n"
    "Deliberate main-checkout op: set TETHER_SKIP_WORKTREE_HOOK=1."
)
print(json.dumps({
    "hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "permissionDecision": "deny",
        "permissionDecisionReason": reason,
    }
}))
'
