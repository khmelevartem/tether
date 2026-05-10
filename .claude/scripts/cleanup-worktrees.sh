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

    remote_gone=false
    unpushed=false
    ! git show-ref --quiet "refs/remotes/origin/$branch" && remote_gone=true
    [ -z "$(git log "main..$branch" --oneline 2>/dev/null)" ] && unpushed=false || unpushed=true

    if $remote_gone && ! $unpushed; then
        echo "cleanup-worktrees: removing $wt_path (branch: $branch, remote deleted, no unpushed commits)"
        git worktree remove "$wt_path" --force 2>/dev/null || true
        git branch -d "$branch" 2>/dev/null || true
    fi
done
