#!/bin/bash
# Removes git worktrees whose branches are already merged into main.
# Runs as a Claude Code Stop hook.

ROOT=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0
cd "$ROOT"

git fetch --prune origin 2>/dev/null || true

git worktree list --porcelain | awk '
    /^worktree / { path = substr($0, 10) }
    /^branch /   { branch = substr($0, 8); sub("^refs/heads/", "", branch) }
    /^$/         { if (path != "" && branch != "") print path "|" branch; path = ""; branch = "" }
' | while IFS='|' read -r wt_path branch; do
    [ "$wt_path" = "$ROOT" ] && continue

    if git branch --merged main 2>/dev/null | grep -qE "^\*? *${branch}$"; then
        echo "cleanup-worktrees: removing $wt_path (branch: $branch, merged into main)"
        git worktree remove "$wt_path" --force 2>/dev/null || true
        git branch -d "$branch" 2>/dev/null || true
    fi
done
