#!/usr/bin/env bash
# Structural invariants of the /implement skill: the steps.md walk (Applies-to
# tags, the drift gate, step ordering / numbering) and the classify script split.
# Split out of select-reviewers.test.sh — these assert the skill's shape, not the
# reviewer roster.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STEPS_MD="$SCRIPT_DIR/../steps.md"

PASS=0
FAIL=0

assert_eq() {
  local label="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then
    PASS=$((PASS + 1))
  else
    FAIL=$((FAIL + 1))
    echo "FAIL [$label]"
    echo "  expected: $expected"
    echo "  actual:   $actual"
  fi
}

# ── steps.md tag presence ────────────────────────────────────────────────────
#
# Every `## Step` carries an `**Applies to:**` line — no step relies on the
# absence of a tag to mean "always run". Assert the untagged set is empty, and
# pin the unconditional steps (`**Applies to:** every run`) by name, not count —
# a count-only check passes if a future edit tags an always-run step while
# untagging a gated one.

UNTAGGED=$(awk '
  /^## Step [0-9]+[a-z]? — /{ if(id!="" && !tagged) print id; id=$0; sub(/^## Step [0-9]+[a-z]? — /,"",id); tagged=0 }
  /^\*\*Applies to:\*\*/{ tagged=1 }
  END{ if(id!="" && !tagged) print id }
' "$STEPS_MD" | sort | tr '\n' ' ' | sed 's/ $//')

assert_eq "every step carries an Applies-to line" "" "$UNTAGGED"

EVERY_RUN=$(awk '
  /^## Step [0-9]+[a-z]? — /{ id=$0; sub(/^## Step [0-9]+[a-z]? — /,"",id) }
  /^\*\*Applies to:\*\* every run/{ print id }
' "$STEPS_MD" | sort | tr '\n' ' ' | sed 's/ $//')

assert_eq "unconditional steps are exactly the always-run set" \
  "check working tree classify the state commit fast-review final-summary full-review push transform input to actions" \
  "$EVERY_RUN"

# The drift gate: classify-state.sh withholds the state when behind main, and the
# only step able to match that partial profile is sync-main. Pin it by name so
# a future edit cannot drop the step or its gate and leave drift prose-handled.

DRIFT_STEP=$(awk '
  /^## Step [0-9]+[a-z]? — /{ id=$0; sub(/^## Step [0-9]+[a-z]? — /,"",id) }
  /^\*\*Applies to:\*\*.*drift=behind/{ print id }
' "$STEPS_MD" | sort | tr '\n' ' ' | sed 's/ $//')

assert_eq "sync-main is the sole drift=behind gate" "sync-main" "$DRIFT_STEP"

# Order is load-bearing: a behind re-entry must hit sync-main before any other
# step. A name-only check passes a regression that reorders them, so pin
# position — sync-main is Step 3 (after read-all + classify-task + classify-state),
# ahead of reentry-reconcile and every work step.

SYNC_N=$(awk '/^## Step [0-9]+ — sync-main$/{print $3}' "$STEPS_MD")
RECONCILE_N=$(awk '/^## Step [0-9]+ — reentry-reconcile$/{print $3}' "$STEPS_MD")

assert_eq "sync-main is Step 3 (after read-all + classify-task + classify-state)" "3" "$SYNC_N"

if [ -n "$SYNC_N" ] && [ -n "$RECONCILE_N" ] && [ "$SYNC_N" -lt "$RECONCILE_N" ]; then
  PASS=$((PASS + 1))
else
  FAIL=$((FAIL + 1))
  echo "FAIL [sync-main precedes reentry-reconcile] — sync=$SYNC_N reconcile=$RECONCILE_N"
fi

# File order IS the sequencing authority, so the `## Step N` numbers must strictly
# increase down the file. Without this, a block moved physically while keeping a
# stale number would desync the walk from the headings and pass every name check.
# Suffixed siblings (14a, 14b) share an integer but order by letter, so compare a
# key of int*100 + letter-position — 14a (1401) < 14b (1402) < 15 (1500).
MONO=$(awk '
  /^## Step [0-9]+[a-z]? — /{
    hdr=$3; n=hdr+0; suf=hdr; gsub(/[0-9]/,"",suf);
    key=n*100 + (suf=="" ? 0 : index("abcdefghijklmnopqrstuvwxyz",suf));
    if(prev!="" && key<=prevkey){bad=1}
    prevkey=key; prev=hdr
  }
  END{ print (bad?"no":"yes") }
' "$STEPS_MD")

assert_eq "step numbers strictly increase in file order" "yes" "$MONO"

# ── Script split: task (type, once) vs state (every run) ─────────────────────
#
# classify.sh was split so a step body never re-derives the stable task type from
# volatile state. Pin the split: both scripts present, the old one gone, and no
# stale `classify.sh` reference left behind in the prose (a half-done rename).

if [ -x "$SCRIPT_DIR/classify-task.sh" ]; then PASS=$((PASS + 1)); else
  FAIL=$((FAIL + 1)); echo "FAIL [classify-task.sh missing or not executable]"; fi

if [ -x "$SCRIPT_DIR/classify-state.sh" ]; then PASS=$((PASS + 1)); else
  FAIL=$((FAIL + 1)); echo "FAIL [classify-state.sh missing or not executable]"; fi

if [ ! -e "$SCRIPT_DIR/classify.sh" ]; then PASS=$((PASS + 1)); else
  FAIL=$((FAIL + 1)); echo "FAIL [classify.sh still present after split]"; fi

STALE=$(grep -rl 'classify\.sh' "$SCRIPT_DIR/.." 2>/dev/null | grep -v '\.test\.sh$' || true)
assert_eq "no stale classify.sh reference in skill prose" "" "$STALE"

# ── Summary ───────────────────────────────────────────────────────────────────

TOTAL=$((PASS + FAIL))
echo "Results: $PASS/$TOTAL passed"
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
