#!/usr/bin/env bash
# Standalone tests for select-reviewers.sh + the steps.md `Applies to:` tags.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$SCRIPT_DIR/select-reviewers.sh"
STEPS_MD="$SCRIPT_DIR/../steps.md"

PASS=0
FAIL=0

run() {
  bash "$SCRIPT" "$@" 2>/dev/null
}

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

assert_contains() {
  local label="$1" needle="$2" haystack="$3"
  if echo "$haystack" | grep -qF "$needle"; then
    PASS=$((PASS + 1))
  else
    FAIL=$((FAIL + 1))
    echo "FAIL [$label] — expected '$needle' in: $haystack"
  fi
}

assert_nonzero_exit() {
  local label="$1"; shift
  local code=0
  bash "$SCRIPT" "$@" >/dev/null 2>&1 || code=$?
  if [ "$code" -ne 0 ]; then
    PASS=$((PASS + 1))
  else
    FAIL=$((FAIL + 1))
    echo "FAIL [$label] — expected non-zero exit, got 0"
  fi
}

inner_line()  { echo "$1" | grep '^inner-loop-reviewers:'; }
wave_a_line() { echo "$1" | grep '^wave-a-reviewers:'; }
wave_b_line() { echo "$1" | grep '^wave-b-reviewers:'; }

# ── Case 1: code feature (no touched) ─────────────────────────────────────────

OUT=$(run code feature "")

assert_eq "c1 inner" \
  "inner-loop-reviewers: review-correctness review-architecture review-guides" \
  "$(inner_line "$OUT")"

assert_eq "c1 wave-a" \
  "wave-a-reviewers: review-dod review-guides review-glossary review-reuse review-architecture review-correctness review-tests" \
  "$(wave_a_line "$OUT")"

# ── Case 2: code feature ui+code+platform ─────────────────────────────────────

OUT=$(run code feature "ui,code,platform")

assert_eq "c2 inner" \
  "inner-loop-reviewers: review-correctness review-architecture review-guides review-platform review-ux-conformance" \
  "$(inner_line "$OUT")"

assert_eq "c2 wave-a" \
  "wave-a-reviewers: review-dod review-guides review-glossary review-reuse review-architecture review-correctness review-tests review-platform review-ux-conformance review-design-system review-visual" \
  "$(wave_a_line "$OUT")"

# ── Case 3: code bugfix ───────────────────────────────────────────────────────

OUT=$(run code bugfix "")

assert_eq "c3 inner" \
  "inner-loop-reviewers: review-correctness review-architecture review-guides" \
  "$(inner_line "$OUT")"

assert_eq "c3 wave-a" \
  "wave-a-reviewers: review-dod review-guides review-glossary review-reuse review-architecture review-correctness review-tests" \
  "$(wave_a_line "$OUT")"

# ── Case 4: code refactor ─────────────────────────────────────────────────────

OUT=$(run code refactor "")

assert_eq "c4 inner" \
  "inner-loop-reviewers: review-architecture review-guides" \
  "$(inner_line "$OUT")"

assert_eq "c4 wave-a" \
  "wave-a-reviewers: review-dod review-guides review-glossary review-reuse review-architecture review-tests" \
  "$(wave_a_line "$OUT")"

# ── Case 5: code infra ───────────────────────────────────────────────────────

OUT=$(run code infra "")

assert_eq "c5 inner" \
  "inner-loop-reviewers: review-correctness review-architecture review-guides" \
  "$(inner_line "$OUT")"

assert_eq "c5 wave-a" \
  "wave-a-reviewers: review-dod review-guides review-glossary review-reuse review-architecture review-correctness" \
  "$(wave_a_line "$OUT")"

# ── Case 7: docs docs docs ───────────────────────────────────────────────────

OUT=$(run docs docs "docs")

assert_eq "c7 inner empty" "inner-loop-reviewers: " "$(inner_line "$OUT")"

assert_eq "c7 wave-a" \
  "wave-a-reviewers: review-dod review-guides review-glossary review-reuse review-consistency" \
  "$(wave_a_line "$OUT")"

# ── Case 8: docs docs docs+engdoc ────────────────────────────────────────────

OUT=$(run docs docs "docs,engdoc")

assert_eq "c8 wave-a" \
  "wave-a-reviewers: review-dod review-guides review-glossary review-reuse review-consistency review-architecture" \
  "$(wave_a_line "$OUT")"

# ── Case 9: docs feature ux-brief ────────────────────────────────────────────

OUT=$(run docs feature "ux-brief")

assert_eq "c9 wave-a" \
  "wave-a-reviewers: review-dod review-guides review-glossary review-reuse review-consistency review-ux-brief" \
  "$(wave_a_line "$OUT")"

# ── Case 9b: code feature touching docs gets review-consistency ──────────────
#
# A code task that also edits docs still earns the cross-cutting consistency
# pass; a code task that touches no docs does not.

OUT=$(run code feature "code,docs")
assert_contains "c9b code+docs has review-consistency" \
  "review-consistency" "$(wave_a_line "$OUT")"
assert_eq "c9b code+docs inner unaffected" \
  "inner-loop-reviewers: review-correctness review-architecture review-guides" \
  "$(inner_line "$OUT")"

OUT=$(run code feature "code")
if echo "$(wave_a_line "$OUT")" | grep -qF "review-consistency"; then
  FAIL=$((FAIL + 1))
  echo "FAIL [c9b code-no-docs omits review-consistency] — unexpectedly present"
else
  PASS=$((PASS + 1))
fi

# ── Case 10: error — no args ──────────────────────────────────────────────────

assert_nonzero_exit "c10 no-args"

# ── Case 11: error — unknown track ───────────────────────────────────────────

assert_nonzero_exit "c11 unknown-track" banana feature ""

# ── Always-present: code track rosters ───────────────────────────────────────

for touched in "" "ui,code,platform"; do
  OUT=$(run code feature "$touched")
  # Always in inner-loop for non-refactor types (narrow roster — compounding-cost reviewers only).
  for reviewer in review-correctness review-guides review-architecture; do
    assert_contains "always-inner $reviewer (touched=$touched)" "$reviewer" "$(inner_line "$OUT")"
  done
  # Always in Wave A (broad final-gate roster).
  for reviewer in review-dod review-guides review-glossary; do
    assert_contains "always-wave-a $reviewer (touched=$touched)" "$reviewer" "$(wave_a_line "$OUT")"
  done
  assert_eq "wave-b review-adversarial (touched=$touched)" \
    "wave-b-reviewers: review-adversarial" "$(wave_b_line "$OUT")"
done

# ── Wave B present on docs track ─────────────────────────────────────────────

OUT=$(run docs docs "docs")
assert_eq "docs wave-b review-adversarial" \
  "wave-b-reviewers: review-adversarial" "$(wave_b_line "$OUT")"

# ── steps.md tag presence ────────────────────────────────────────────────────
#
# Every `## Step` carries an `**Applies to:**` line — no step relies on the
# absence of a tag to mean "always run". Assert the untagged set is empty, and
# pin the unconditional steps (`**Applies to:** every run`) by name, not count —
# a count-only check passes if a future edit tags an always-run step while
# untagging a gated one.

UNTAGGED=$(awk '
  /^## Step [0-9]+ — /{ if(id!="" && !tagged) print id; id=$0; sub(/^## Step [0-9]+ — /,"",id); tagged=0 }
  /^\*\*Applies to:\*\*/{ tagged=1 }
  END{ if(id!="" && !tagged) print id }
' "$STEPS_MD" | sort | tr '\n' ' ' | sed 's/ $//')

assert_eq "every step carries an Applies-to line" "" "$UNTAGGED"

EVERY_RUN=$(awk '
  /^## Step [0-9]+ — /{ id=$0; sub(/^## Step [0-9]+ — /,"",id) }
  /^\*\*Applies to:\*\* every run/{ print id }
' "$STEPS_MD" | sort | tr '\n' ' ' | sed 's/ $//')

assert_eq "unconditional steps are exactly the always-run set" \
  "classify commit-pr final-summary full-review" "$EVERY_RUN"

# The drift gate: classify.sh withholds the profile when behind main, and the
# only step able to match that partial profile is sync-main. Pin it by name so
# a future edit cannot drop the step or its gate and leave drift prose-handled.

DRIFT_STEP=$(awk '
  /^## Step [0-9]+ — /{ id=$0; sub(/^## Step [0-9]+ — /,"",id) }
  /^\*\*Applies to:\*\*.*drift=behind/{ print id }
' "$STEPS_MD" | sort | tr '\n' ' ' | sed 's/ $//')

assert_eq "sync-main is the sole drift=behind gate" "sync-main" "$DRIFT_STEP"

# ── Summary ───────────────────────────────────────────────────────────────────

TOTAL=$((PASS + FAIL))
echo "Results: $PASS/$TOTAL passed"
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
