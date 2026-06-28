#!/usr/bin/env bash
# Emits the STABLE task fact for /implement — the issue's type.
# A pure function of the issue label, so run it ONCE per walk and hold the type
# in context; re-running yields the same answer. Current state (reentry / pr /
# drift / touched) is the volatile axis and lives in classify-state.sh.
# Output: key=value lines to stdout (no files written).
# Usage: classify-task.sh <issue-number>   (issue is required — resolving which
#        issue we are on is classify-state.sh's job; pass its `issue=` here)
# Emits: issue, type

set -euo pipefail

if [ $# -lt 1 ] || [ -z "$1" ]; then
  echo "classify-task.sh: issue number is required" >&2
  exit 1
fi
if ! echo "$1" | grep -qE '^[0-9]+$'; then
  echo "classify-task.sh: issue number must be numeric, got: $1" >&2
  exit 1
fi

ISSUE="$1"
echo "issue=$ISSUE"

LABELS_JSON=$(gh issue view "$ISSUE" --json labels 2>/dev/null || echo '{"labels":[]}')
TYPE=$(echo "$LABELS_JSON" | python3 -c "
import json, sys
aliases = {
  'enhancement': 'feature',
  'bug': 'bugfix',
  'documentation': 'docs',
}
types = {'feature','bugfix','refactor','infra','docs','dependency'}
data = json.loads(sys.stdin.read())
names = [l['name'] for l in data.get('labels', [])]
for n in names:
  if n in types:
    print(n)
    sys.exit(0)
  if n in aliases:
    print(aliases[n])
    sys.exit(0)
print('none')
" 2>/dev/null || echo "unknown")

echo "type=$TYPE"
