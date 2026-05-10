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

    # Remote branch gone after fetch --prune AND a merged PR exists for this branch.
    # git branch --merged / merge-base --is-ancestor both fail for squash merges;
    # querying GitHub is the only reliable signal across all merge strategies.
    remote_gone=false
    ! git show-ref --quiet "refs/remotes/origin/$branch" && remote_gone=true

    if $remote_gone; then
        merged=$(gh pr list --state merged --head "$branch" --json number --jq 'length' 2>/dev/null)
        if [ "$merged" -gt 0 ] 2>/dev/null; then
            echo "cleanup-worktrees: removing $wt_path (branch: $branch, PR merged)"
            git worktree remove "$wt_path" --force 2>/dev/null || true
            git branch -d "$branch" 2>/dev/null || true
        fi
    fi
done
