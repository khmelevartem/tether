#!/usr/bin/env bash
# Standalone tests for select-pipeline.sh.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$SCRIPT_DIR/select-pipeline.sh"

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

assert_absent() {
  local label="$1" needle="$2" haystack="$3"
  if echo "$haystack" | grep -qF "$needle"; then
    FAIL=$((FAIL + 1))
    echo "FAIL [$label] — expected '$needle' absent from: $haystack"
  else
    PASS=$((PASS + 1))
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

steps_line() { echo "$1" | grep '^steps:'; }
inner_line()  { echo "$1" | grep '^inner-loop-reviewers:'; }
wave_a_line() { echo "$1" | grep '^wave-a-reviewers:'; }
wave_b_line() { echo "$1" | grep '^wave-b-reviewers:'; }

# ── Case 1: code feature fresh (no touched) ───────────────────────────────────

OUT=$(run code feature fresh "")

assert_eq "c1 steps" \
  "steps: classify recon early-gates plan inner-loop simplify full-review runtime-verify commit-pr final-summary" \
  "$(steps_line "$OUT")"

assert_eq "c1 inner" \
  "inner-loop-reviewers: review-dod review-correctness review-guides review-glossary review-architecture review-tests" \
  "$(inner_line "$OUT")"

assert_eq "c1 wave-a" \
  "wave-a-reviewers: review-dod review-guides review-glossary review-reuse review-architecture review-correctness review-tests" \
  "$(wave_a_line "$OUT")"

# ── Case 2: code feature fresh ui+code+platform ───────────────────────────────

OUT=$(run code feature fresh "ui,code,platform")

assert_eq "c2 inner" \
  "inner-loop-reviewers: review-dod review-correctness review-guides review-glossary review-architecture review-tests review-platform review-ux-conformance review-design-system review-visual" \
  "$(inner_line "$OUT")"

assert_eq "c2 wave-a" \
  "wave-a-reviewers: review-dod review-guides review-glossary review-reuse review-architecture review-correctness review-tests review-platform review-ux-conformance review-design-system review-visual" \
  "$(wave_a_line "$OUT")"

# ── Case 3: code bugfix fresh ─────────────────────────────────────────────────

OUT=$(run code bugfix fresh "")

assert_eq "c3 inner" \
  "inner-loop-reviewers: review-dod review-correctness review-guides review-glossary review-architecture review-tests" \
  "$(inner_line "$OUT")"

assert_eq "c3 wave-a" \
  "wave-a-reviewers: review-dod review-guides review-glossary review-reuse review-architecture review-correctness review-tests" \
  "$(wave_a_line "$OUT")"

# ── Case 4: code refactor fresh ───────────────────────────────────────────────

OUT=$(run code refactor fresh "")

assert_eq "c4 inner" \
  "inner-loop-reviewers: review-dod review-guides review-glossary review-architecture review-tests" \
  "$(inner_line "$OUT")"

assert_eq "c4 wave-a" \
  "wave-a-reviewers: review-dod review-guides review-glossary review-reuse review-architecture review-tests" \
  "$(wave_a_line "$OUT")"

# ── Case 5: code infra fresh ─────────────────────────────────────────────────

OUT=$(run code infra fresh "")

assert_eq "c5 inner" \
  "inner-loop-reviewers: review-dod review-correctness review-guides review-glossary review-architecture" \
  "$(inner_line "$OUT")"

assert_eq "c5 wave-a" \
  "wave-a-reviewers: review-dod review-guides review-glossary review-reuse review-architecture review-correctness" \
  "$(wave_a_line "$OUT")"

# ── Case 6: code feature pr-feedback ui+code ─────────────────────────────────

OUT=$(run code feature pr-feedback "ui,code")

assert_eq "c6 steps" \
  "steps: classify reentry-reconcile recon inner-loop simplify full-review runtime-verify commit-pr final-summary" \
  "$(steps_line "$OUT")"

# ── Case 7: docs docs fresh docs ─────────────────────────────────────────────

OUT=$(run docs docs fresh "docs")

assert_eq "c7 steps" \
  "steps: classify recon layer-classify docs-dispatch consistency full-review commit-pr final-summary" \
  "$(steps_line "$OUT")"

assert_eq "c7 inner empty" "inner-loop-reviewers: " "$(inner_line "$OUT")"

assert_eq "c7 wave-a" \
  "wave-a-reviewers: review-dod review-guides review-glossary review-reuse" \
  "$(wave_a_line "$OUT")"

# ── Case 8: docs docs fresh docs+engdoc ──────────────────────────────────────

OUT=$(run docs docs fresh "docs,engdoc")

assert_eq "c8 wave-a" \
  "wave-a-reviewers: review-dod review-guides review-glossary review-reuse review-architecture" \
  "$(wave_a_line "$OUT")"

# ── Case 9: docs feature fresh ux-brief ──────────────────────────────────────

OUT=$(run docs feature fresh "ux-brief")

assert_eq "c9 wave-a" \
  "wave-a-reviewers: review-dod review-guides review-glossary review-reuse review-ux-brief" \
  "$(wave_a_line "$OUT")"

# ── Case 10: docs docs pr-feedback docs+engdoc ───────────────────────────────

OUT=$(run docs docs pr-feedback "docs,engdoc")

assert_eq "c10 steps" \
  "steps: classify reentry-reconcile consistency full-review commit-pr final-summary" \
  "$(steps_line "$OUT")"

# ── Case 11: error — no args ──────────────────────────────────────────────────

assert_nonzero_exit "c11 no-args"

# ── Case 12: error — unknown track ───────────────────────────────────────────

assert_nonzero_exit "c12 unknown-track" banana feature fresh ""

# ── Always-present: code track rosters ───────────────────────────────────────

for touched in "" "ui,code,platform"; do
  OUT=$(run code feature fresh "$touched")
  for reviewer in review-dod review-guides review-glossary; do
    assert_contains "always-inner $reviewer (touched=$touched)" "$reviewer" "$(inner_line "$OUT")"
    assert_contains "always-wave-a $reviewer (touched=$touched)" "$reviewer" "$(wave_a_line "$OUT")"
  done
  assert_eq "wave-b review-adversarial (touched=$touched)" \
    "wave-b-reviewers: review-adversarial" "$(wave_b_line "$OUT")"
done

# ── Wave B present on docs track ─────────────────────────────────────────────

OUT=$(run docs docs fresh "docs")
assert_eq "docs wave-b review-adversarial" \
  "wave-b-reviewers: review-adversarial" "$(wave_b_line "$OUT")"

# ── Summary ───────────────────────────────────────────────────────────────────

TOTAL=$((PASS + FAIL))
echo "Results: $PASS/$TOTAL passed"
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
