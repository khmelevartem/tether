#!/usr/bin/env bash
# Structured task dossier for a retrospective: issue, transcript, PR, commits.
# Usage: task-dossier.sh <issue> [<pr>] [<merge-commit>]
set -uo pipefail
N="${1:?issue number required}"
PR="${2:-}"
MERGE="${3:-}"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "===== ISSUE #$N ====="
gh issue view "$N" --json title,body,comments

echo
echo "===== TRANSCRIPT — primary source, read user turns first ====="
"$here/find-transcript.sh"

if [ -n "$PR" ]; then
  echo
  echo "===== PR #$PR ====="
  gh pr view "$PR" --json title,body,commits,comments,reviews
fi

if [ -n "$MERGE" ]; then
  echo
  echo "===== MERGE COMMIT $MERGE ====="
  git log --oneline "$MERGE" -1
fi
