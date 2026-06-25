#!/usr/bin/env bash
# Renames the current worktree branch to <issue>-<title-slug> so `git worktree
# list` and classify.sh map the checkout back to its issue.
#
# Branch only — the worktree directory is never moved: a live session's working
# directory would vanish mid-run and its transcript (keyed on the worktree path)
# would be orphaned. Hooks key on live git state, not the name, so the rename is
# transparent to them.
#
# Idempotent: a no-op once the branch already starts with the issue number.
# Usage: ensure-branch.sh <issue-number>

set -euo pipefail

issue="${1:?usage: ensure-branch.sh <issue-number>}"
branch=$(git rev-parse --abbrev-ref HEAD)

case "$branch" in
  [0-9]*-*)    exit 0 ;;                                                    # already issue-numbered
  main|master) echo "refusing to rename $branch — run from a worktree" >&2; exit 1 ;;
esac

slug=$(gh issue view "$issue" --json title -q .title \
  | tr '[:upper:]' '[:lower:]' \
  | sed -E 's/[^a-z0-9]+/-/g; s/^-+//; s/-+$//' \
  | cut -c1-50 | sed -E 's/-+$//')

git branch -m "$branch" "${issue}-${slug}"
echo "branch renamed: $branch -> ${issue}-${slug}"
