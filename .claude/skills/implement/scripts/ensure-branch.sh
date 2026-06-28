#!/usr/bin/env bash
# Renames the current worktree branch to <issue>-<title-slug> so the worktree
# listing and state classification map the checkout back to its issue. Only the
# branch ref is renamed; the worktree directory is left untouched.
#
# Idempotent: a no-op once the branch already starts with the issue number.
# Usage: ensure-branch.sh <issue-number>

set -euo pipefail

issue="${1:?usage: ensure-branch.sh <issue-number>}"
branch=$(git rev-parse --abbrev-ref HEAD)

case "$branch" in
  [0-9]*-*)   echo "branch name ok"; exit 0 ;;                                          # already issue-numbered
  main|master) echo "refusing to rename $branch — run from a worktree" >&2; exit 1 ;;
esac

slug=$(gh issue view "$issue" --json title -q .title \
  | tr '[:upper:]' '[:lower:]' \
  | sed -E 's/[^a-z0-9]+/-/g; s/^-+//; s/-+$//' \
  | cut -c1-50 | sed -E 's/-+$//')

[ -n "$slug" ] || { echo "empty slug from issue $issue title — check the title" >&2; exit 1; }

git branch -m "$branch" "${issue}-${slug}"
echo "branch renamed: $branch -> ${issue}-${slug}"
