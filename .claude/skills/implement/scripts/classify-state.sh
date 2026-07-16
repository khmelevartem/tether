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
#   exit 2 — terminal: no issue resolves, or the checkout is a stray worktree
#            whose ops would corrupt main; the walk stops (halt-unresolved step).
#   exit 3 — recoverable: branch is behind main; the walk syncs and retries
#            (sync-main step). Callers distinguish by the emitted key
#            (reentry=unknown/stray-worktree vs drift=behind), not the exit code.
#
# track+type are the STABLE axes: classify-task.sh computes them once per issue
# and the fresh walk persists them to the profile (keyed by issue, so a marker
# from another issue in the same git dir is never re-surfaced); this script only
# re-surfaces the persisted values so they outlive the session that computed them.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG="$SCRIPT_DIR/../../../project.json"

# ── Resolve current branch ────────────────────────────────────────────────────

BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")
[ -z "$BRANCH" ] && BRANCH="HEAD"

# ── Preflight: reject a stray (unregistered) worktree ────────────────────────
# A dir under .claude/worktrees/ never `git worktree add`-ed resolves its git dir
# to the MAIN checkout, so branch/profile ops silently mutate main. A registered
# worktree resolves under .git/worktrees/; halt before any mutation otherwise.
case "$PWD" in
  */.claude/worktrees/*)
    GIT_DIR_ABS="$(git rev-parse --absolute-git-dir 2>/dev/null || echo "")"
    case "$GIT_DIR_ABS" in
      */.git/worktrees/*) : ;;
      *)
        echo "issue=unknown"
        echo "reentry=stray-worktree"
        echo "status=blocked"
        echo "classify-state.sh: stray worktree — '$PWD' is under .claude/worktrees/ but git resolves to the main checkout ('${GIT_DIR_ABS:-unresolved}'); git ops would corrupt main. Rebuild it with 'git worktree add' first." >&2
        exit 2
        ;;
    esac
    ;;
esac

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
try:
  with open('$CONFIG') as f:
    config = json.load(f)
except (FileNotFoundError, json.JSONDecodeError):
  config = {}
pattern_template = config.get('git', {}).get('prTitlePattern', '^#<issue>')
pattern = pattern_template.replace('<issue>', '([0-9]+)')
data = json.loads(sys.stdin.read())
title = data[0]['title'] if data else ''
m = re.match(pattern, title)
print(m.group(1) if m else '')
" 2>/dev/null || echo "")
fi

if [ -z "$CURRENT_ISSUE" ]; then
  CURRENT_ISSUE=$(python3 -c "
import json, re, sys
try:
  with open('$CONFIG') as f:
    data = json.load(f)
except (FileNotFoundError, json.JSONDecodeError):
  data = {}
patterns = data.get('git', {}).get('branchPatterns', [])
branch = '$BRANCH'
for p in patterns:
  m = re.match(p, branch)
  if m:
    digits = re.search(r'[0-9]+', m.group(0))
    if digits:
      print(digits.group(0))
      sys.exit(0)
" 2>/dev/null || true)
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
try:
  with open('$CONFIG') as f:
    config = json.load(f)
except (FileNotFoundError, json.JSONDecodeError):
  config = {}
pattern_template = config.get('git', {}).get('prTitlePattern', '^#<issue>')
data = json.loads(sys.stdin.read())
issue = '$ISSUE'
pattern = pattern_template.replace('<issue>', re.escape(issue)) + r'([: ]|\$)'
for pr in data:
  if re.match(pattern, pr['title']):
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

TOUCHED=$(echo "$DIFF_FILES" | python3 "$SCRIPT_DIR/derive-touched.py" "$CONFIG" 2>/dev/null || true)

echo "touched=$TOUCHED"

# ── Surface the persisted profile (track, type) ──────────────────────────────
# Re-emit the persisted track+type (keyed by issue) so every walk — including a
# pr-feedback re-entry that skips the fresh-only classify-task step — reads them
# mechanically instead of from memory.
PROFILE="$(git rev-parse --git-dir 2>/dev/null)/implement-profile-$ISSUE"
if [ -f "$PROFILE" ]; then
  cat "$PROFILE"
fi
