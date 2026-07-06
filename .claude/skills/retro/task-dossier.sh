#!/usr/bin/env bash
# Structured task dossier for a retrospective: issue, transcript, PR, commits.
# Usage: task-dossier.sh <issue> [<pr>] [<merge-commit>]
# PR and merge-commit are derived from the issue when omitted.
set -uo pipefail
N="${1:?issue number required}"
PR="${2:-}"
MERGE="${3:-}"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Implementing PR: title starts with "#<issue>:" (project convention). Prefer
# the merged one over closed/superseded duplicates by sorting merged-first.
if [ -z "$PR" ]; then
  PR="$(gh pr list --state all --search "$N in:title" --json number,title,mergedAt \
        --jq '.[] | [(.mergedAt // ""), (.number|tostring), .title] | @tsv' 2>/dev/null \
      | awk -F'\t' -v n="$N" '$3 ~ ("^#" n "[: ]") {print}' \
      | sort -r | head -1 | cut -f2)"
fi
if [ -z "$MERGE" ] && [ -n "$PR" ]; then
  MERGE="$(gh pr view "$PR" --json mergeCommit --jq '.mergeCommit.oid // empty' 2>/dev/null)"
fi

echo "===== ISSUE #$N ====="
gh issue view "$N" --json title,body,comments

echo
echo "===== TRANSCRIPT — primary source, read user turns first ====="
"$here/find-transcript.sh"

if [ -n "$PR" ]; then
  echo
  echo "===== PR #$PR ====="
  gh pr view "$PR" --json title,body,commits,comments,reviews
else
  echo
  echo "===== PR — none found for #$N ====="
fi

if [ -n "$MERGE" ]; then
  echo
  echo "===== MERGE COMMIT $MERGE ====="
  git log --oneline "$MERGE" -1
fi
