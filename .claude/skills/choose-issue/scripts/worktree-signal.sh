#!/usr/bin/env bash
# Prints local worktree branch(es) already working the given issue — any output
# is in-progress evidence (mark 🟡); no output means no match. Exits 0 either way.
# Usage: worktree-signal.sh <issue-number>

set -euo pipefail

issue="${1:?usage: worktree-signal.sh <issue-number>}"

git worktree list --porcelain | grep '^branch ' | sed 's|^branch refs/heads/||' | while read -r b; do
  n="$(printf '%s' "$b" | grep -oE '^[0-9]+' || true)"
  if   [ "$n" = "$issue" ]; then echo "$b"                                                   # primary: branch renamed to <N>-slug
  elif [ -z "$n" ] && git log "main..$b" --oneline | grep -qE "^[a-f0-9]+ #${issue}:"; then echo "$b" # fallback: pre-rename branch, #<N>: commit prefix among commits unique to it
  fi
done || true
