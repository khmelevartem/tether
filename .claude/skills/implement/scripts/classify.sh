#!/usr/bin/env bash
# Emits mechanical profile facts for /implement.
# Output: key=value lines to stdout (no files written).
# Usage: classify.sh [<issue-number>]
# touched values: ui, code, platform, docs, engdoc, claude, ux-brief

set -euo pipefail

# ── Resolve current branch ────────────────────────────────────────────────────

BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")
[ -z "$BRANCH" ] && BRANCH="HEAD"

# ── Resolve current worktree's issue (CURRENT_ISSUE) ─────────────────────────

HEAD_PR=""
CURRENT_ISSUE=""

HEAD_PR_JSON=$(gh pr list --head "$BRANCH" --state open --json number,title 2>/dev/null || echo "[]")
HEAD_PR=$(echo "$HEAD_PR_JSON" | python3 -c "
import json, sys
data = json.loads(sys.stdin.read())
print(data[0]['number'] if data else '')
" 2>/dev/null || echo "")

if [ -n "$HEAD_PR" ]; then
  CURRENT_ISSUE=$(echo "$HEAD_PR_JSON" | python3 -c "
import json, sys, re
data = json.loads(sys.stdin.read())
title = data[0]['title'] if data else ''
m = re.match(r'^#([0-9]+)', title)
print(m.group(1) if m else '')
" 2>/dev/null || echo "")
fi

if [ -z "$CURRENT_ISSUE" ]; then
  CURRENT_ISSUE=$(printf '%s' "$BRANCH" | grep -oE '^(feature|docs)/[0-9]+' | grep -oE '[0-9]+' || true)
fi
if [ -z "$CURRENT_ISSUE" ]; then
  CURRENT_ISSUE=$(printf '%s' "$BRANCH" | grep -oE '^[0-9]+' || true)
fi

# ── Resolve target issue number ───────────────────────────────────────────────

if [ $# -ge 1 ] && [ -n "$1" ]; then
  ISSUE="$1"
else
  ISSUE="$CURRENT_ISSUE"
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

if [ $# -ge 1 ] && [ -n "$1" ] && ! echo "$1" | grep -qE '^[0-9]+$'; then
  echo "classify.sh: issue number must be numeric, got: $1" >&2
  exit 1
fi

echo "issue=$ISSUE"

# ── Detect open PR and drift for ISSUE ───────────────────────────────────────

if [ -n "$HEAD_PR" ] && [ -n "$CURRENT_ISSUE" ] && [ "$CURRENT_ISSUE" = "$ISSUE" ]; then
  PR_NUMBER="$HEAD_PR"
else
  PR_NUMBER=$(gh pr list --state open --limit 500 --json number,title 2>/dev/null | python3 -c "
import json, sys, re
data = json.loads(sys.stdin.read())
issue = '$ISSUE'
for pr in data:
  if re.match(r'^#' + re.escape(issue) + r'([: ]|\$)', pr['title']):
    print(pr['number'])
    sys.exit(0)
print('-')
" 2>/dev/null || echo "-")
fi

if [ "$PR_NUMBER" = "-" ]; then
  echo "reentry=fresh"
  echo "pr=-"
  echo "drift=na"
else
  echo "reentry=pr-feedback"
  echo "pr=$PR_NUMBER"
  git fetch origin main --quiet 2>/dev/null || true
  if git merge-base --is-ancestor origin/main HEAD 2>/dev/null; then
    echo "drift=up-to-date"
  else
    echo "drift=behind"
  fi
fi

# ── Resolve type from labels ──────────────────────────────────────────────────

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

# ── Resolve touched set ───────────────────────────────────────────────────────
# Emit diff only when this worktree owns ISSUE (or worktree issue is unknown,
# in which case the diff is the best available signal).

MATCH=false
if [ -z "$CURRENT_ISSUE" ] || [ "$CURRENT_ISSUE" = "$ISSUE" ]; then
  MATCH=true
fi

if $MATCH; then
  DIFF_FILES=$(git diff --name-only "origin/main...HEAD" 2>/dev/null || true)
else
  DIFF_FILES=""
fi

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
" 2>/dev/null || true)

echo "touched=$TOUCHED"
