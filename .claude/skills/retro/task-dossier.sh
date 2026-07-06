#!/usr/bin/env bash
# Structured task dossier for a retrospective: issue, transcript, PR history, commits.
# Usage: task-dossier.sh <issue> [<pr>] [<merge-commit>]
#
# When the PR is omitted, every PR whose title starts with "#<issue>:" is kept —
# a superseded or closed first attempt is part of the task history and is prime
# retro material, not noise to drop. PRs are ordered merged-first.
set -uo pipefail
N="${1:?issue number required}"
PR_OVERRIDE="${2:-}"
MERGE="${3:-}"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "===== ISSUE #$N ====="
gh issue view "$N" --json title,body,comments

echo
echo "===== TRANSCRIPT — primary source, read user turns first ====="
"$here/find-transcript.sh"

prs=()
if [ -n "$PR_OVERRIDE" ]; then
  prs=("$PR_OVERRIDE")
else
  while IFS= read -r pr; do
    [ -n "$pr" ] && prs+=("$pr")
  done < <(gh pr list --state all --search "$N in:title" --json number,title,mergedAt \
             --jq '.[] | [(.mergedAt // ""), (.number|tostring), .title] | @tsv' 2>/dev/null \
           | awk -F'\t' -v n="$N" '$3 ~ ("^#" n "[: ]") {print}' \
           | sort -r | cut -f2)
fi

if [ "${#prs[@]}" -eq 0 ]; then
  echo
  echo "===== PR — none found for #$N ====="
else
  echo
  echo "===== PR HISTORY for #$N (${#prs[@]}) — a closed/superseded attempt is task history, read it ====="
  for pr in "${prs[@]}"; do
    gh pr view "$pr" --json number,state,mergedAt,title \
      --jq '"#\(.number)\t\(.state)\t\(.mergedAt // "-")\t\(.title)"'
  done
  for pr in "${prs[@]}"; do
    echo
    echo "===== PR #$pr ====="
    gh pr view "$pr" --json title,state,mergedAt,body,commits,comments,reviews
  done

  if [ -z "$MERGE" ]; then
    MERGE="$(gh pr view "${prs[0]}" --json mergeCommit --jq '.mergeCommit.oid // empty' 2>/dev/null)"
  fi
fi

if [ -n "$MERGE" ]; then
  echo
  echo "===== MERGE COMMIT $MERGE ====="
  git log --oneline "$MERGE" -1
fi
