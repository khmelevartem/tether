#!/usr/bin/env bash
# PreToolUse hook: reads tool input JSON from stdin; emits a deny hookSpecificOutput to stdout on violation.

set -euo pipefail

[ "${TETHER_SKIP_WORKTREE_HOOK:-}" = "1" ] && exit 0

input=$(cat)

# Cheap short-circuit: skip full JSON parse when no worktree path is present.
case "$input" in
  *'/.claude/worktrees/'*) ;;
  *) exit 0 ;;
esac

printf '%s' "$input" | python3 -c '
import json, os, sys

try:
    data = json.loads(sys.stdin.read())
except json.JSONDecodeError:
    sys.exit(0)

tool = data.get("tool_name", "")
if tool not in ("Edit", "Write"):
    sys.exit(0)

cwd = data.get("cwd", "")
fp = (data.get("tool_input") or {}).get("file_path", "")
if not cwd or not fp:
    sys.exit(0)

marker = "/.claude/worktrees/"  # worktree layout convention: <repo-root>/.claude/worktrees/<name>/
if marker not in cwd:
    sys.exit(0)

repo_root = cwd.rsplit(marker, 1)[0]
wt_name = cwd.rsplit(marker, 1)[1].split("/")[0]
worktree_root = repo_root + marker + wt_name

abs_fp = os.path.normpath(fp if os.path.isabs(fp) else os.path.join(cwd, fp))

def under(p, root):
    root = root.rstrip("/")
    return p == root or p.startswith(root + "/")

if not under(abs_fp, repo_root):
    sys.exit(0)

if under(abs_fp, worktree_root):
    sys.exit(0)

reason = (
    f"Blocked write outside the active worktree.\n"
    f"file_path: {abs_fp}\n"
    f"expected root: {worktree_root}\n"
    f"Edit files only inside this worktree. "
    f"For a deliberate cross-worktree write, set TETHER_SKIP_WORKTREE_HOOK=1."
)
print(json.dumps({
    "hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "permissionDecision": "deny",
        "permissionDecisionReason": reason,
    }
}))
'
