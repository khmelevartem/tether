#!/usr/bin/env bash
# Emits mechanical profile facts for /implement.
# Output: key=value lines to stdout (no files written).
# Usage: classify.sh [<issue-number>]
# touched values: ui, code, platform, docs, engdoc, claude, ux-brief

set -euo pipefail

# ── Resolve issue number ────────────────────────────────────────────────────

if [ $# -ge 1 ] && [ -n "$1" ]; then
  ISSUE="$1"
else
  BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")
  ISSUE=$(printf '%s' "$BRANCH" | grep -oE '^(feature|docs)/[0-9]+' | grep -oE '[0-9]+' || echo "")
  if [ -z "$ISSUE" ]; then
    ISSUE=$(printf '%s' "$BRANCH" | grep -oE '^[0-9]+' || echo "")
  fi
fi

if [ -z "$ISSUE" ]; then
  echo "issue=unknown"
  echo "reentry=unknown"
  echo "pr=-"
  echo "drift=unknown"
  echo "type=unknown"
  echo "touched="
  exit 0
fi

echo "issue=$ISSUE"

# ── Detect existing open PR ─────────────────────────────────────────────────

PR_JSON=$(gh pr list --search "issue:#${ISSUE}" --state open --json number,headRefName 2>/dev/null || echo "[]")
PR_NUMBER=$(echo "$PR_JSON" | python3 -c "
import json, sys
data = json.loads(sys.stdin.read())
print(data[0]['number'] if data else '-')
" 2>/dev/null || echo "-")

if [ "$PR_NUMBER" = "-" ]; then
  echo "reentry=fresh"
  echo "pr=-"
  echo "drift=na"
else
  echo "reentry=pr-feedback"
  echo "pr=$PR_NUMBER"
  # Check drift only when a PR exists (HEAD branch is meaningful)
  if git merge-base --is-ancestor origin/main HEAD 2>/dev/null; then
    echo "drift=up-to-date"
  else
    echo "drift=behind"
  fi
fi

# ── Resolve type from labels ────────────────────────────────────────────────

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

# ── Resolve touched set from live committed diff ─────────────────────────────

DIFF_FILES=$(git diff --name-only "origin/main...HEAD" 2>/dev/null || echo "")

TOUCHED=$(echo "$DIFF_FILES" | python3 -c "
import sys, re

lines = [l.strip() for l in sys.stdin.read().splitlines() if l.strip()]
result = set()

TEST_SOURCESET_RE = re.compile(r'/(commonTest|androidUnitTest|androidInstrumentedTest|iosTest|jvmTest|desktopTest|appleTest)/')
TEST_FILENAME_RE = re.compile(r'Tests?\.kt$')

for f in lines:
  # ux-brief must be tested before docs (more specific)
  if re.match(r'docs/product/features/.+/ux-brief\.md', f):
    result.add('ux-brief')
  # compose UI
  if re.match(r'composeApp/src/', f):
    result.add('ui')
    result.add('code')
  # platform source sets
  if re.search(r'/(androidMain|appleMain|iosMain|jvmMain|desktopMain)/', f):
    result.add('platform')
  # general source (exclude test source sets and test filenames)
  if re.match(r'.+/src/', f) and not TEST_SOURCESET_RE.search(f) and not TEST_FILENAME_RE.search(f):
    result.add('code')
  if f.startswith('docs/'):
    result.add('docs')
    if f.startswith('docs/engineering/'):
      result.add('engdoc')
  if f.startswith('.claude/'):
    result.add('claude')

print(','.join(sorted(result)))
" 2>/dev/null || echo "")

echo "touched=$TOUCHED"
