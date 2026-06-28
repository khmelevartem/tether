#!/usr/bin/env bash
# Emits the per-run state for /implement: the VOLATILE facts (issue, reentry, pr,
# drift, touched) plus the persisted STABLE profile (track, type) re-surfaced from
# the git dir. Re-run every pass (walk start, and after each commit to refresh
# `touched`).
# Output: key=value lines to stdout (writes no files; only reads the profile).
# Usage: classify-state.sh [<issue-number>]
# Emits: issue, reentry, pr, drift, touched [, status] [, track, type]
# touched values: ui, code, platform, docs, engdoc, claude, ux-brief
#
# Non-zero exit signals blocked state, and the two are NOT the same:
#   exit 2 — terminal: no issue resolves; the walk stops (halt-unresolved step).
#   exit 3 — recoverable: branch is behind main; the walk syncs and retries
#            (sync-main step). Callers distinguish by the emitted key
#            (reentry=unknown vs drift=behind), not by the exit code alone.
#
# track+type are the STABLE axes: classify-task.sh computes them once per issue
# and the fresh walk persists them to the profile; this script only re-surfaces
# the persisted values so they outlive the session that computed them.

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
  # No issue resolves from the arg, the branch, or an open PR — there is nothing
  # to walk. Withhold the rest of the state and exit non-zero so the walk halts
  # here mechanically rather than falling through to the every-run steps with
  # empty context (the SKILL.md "STOP if neither resolves an issue" rule, enforced
  # by the exit code instead of reading discipline).
  echo "issue=unknown"
  echo "reentry=unknown"
  echo "status=blocked"
  echo "classify-state.sh: no issue resolved from the branch or an open PR — run /implement <N> or check out the issue branch, then re-run." >&2
  exit 2
fi

if [ $# -ge 1 ] && [ -n "$1" ] && ! echo "$1" | grep -qE '^[0-9]+$'; then
  echo "classify-state.sh: issue number must be numeric, got: $1" >&2
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
  git fetch origin main --quiet 2>/dev/null || true
  if ! git merge-base --is-ancestor origin/main HEAD 2>/dev/null; then
    # Behind main: withhold the rest of the state (reentry/pr/touched) and exit
    # non-zero. Only drift=behind + status=blocked are emitted, so no reentry/track
    # step matches — sync-main (Applies to: drift=behind) is the sole legal next
    # step, and it loops back here after /pull-main.
    echo "drift=behind"
    echo "status=blocked"
    echo "classify-state.sh: branch is behind origin/main — run /pull-main, then re-run classify-state.sh before proceeding." >&2
    exit 3
  fi
  echo "reentry=pr-feedback"
  echo "pr=$PR_NUMBER"
  echo "drift=up-to-date"
fi

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

# ── Surface the persisted profile (track, type) ──────────────────────────────
# classify-task computes track+type once on a fresh walk and persists them to the
# per-worktree git dir (survives re-entry, never appears in `git status`). Re-emit
# them here so every walk — including a pr-feedback re-entry that skips the
# fresh-only classify-task step — reads them mechanically instead of from memory.
PROFILE="$(git rev-parse --git-dir 2>/dev/null)/implement-profile"
if [ -f "$PROFILE" ]; then
  cat "$PROFILE"
fi
