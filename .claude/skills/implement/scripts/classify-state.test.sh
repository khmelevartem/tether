#!/usr/bin/env bash
# Golden tests for the touched-bucket derivation classify-state.sh delegates to
# derive-touched.py, against the repo's live .claude/project.json.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DERIVE="$SCRIPT_DIR/derive-touched.py"
CONFIG="$SCRIPT_DIR/../../../project.json"

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

derive() {
  printf '%s\n' "$@" | python3 "$DERIVE" "$CONFIG"
}

# ── Case 1: one file per bucket ──────────────────────────────────────────────

OUT=$(derive \
  "docs/product/features/foo/ux-brief.md" \
  "composeApp/src/commonMain/kotlin/Foo.kt" \
  "shared/src/androidMain/kotlin/Bar.kt" \
  "shared/src/commonTest/kotlin/BazTests.kt" \
  "shared/src/commonMain/kotlin/QuxTests.kt" \
  "docs/engineering/foo.md" \
  "docs/glossary.md" \
  ".claude/skills/implement/steps.md" \
  "README.md")

assert_eq "all buckets fire, tests excluded from code" \
  "claude,code,docs,engdoc,platform,ui,ux-brief" "$OUT"

# ── Case 2: empty file list yields empty touched ────────────────────────────

OUT=$(derive)
assert_eq "empty diff yields empty touched" "" "$OUT"

# ── Case 3: docs outside docs/engineering/ excludes engdoc ──────────────────

OUT=$(derive "docs/glossary.md")
assert_eq "docs without engineering/ omits engdoc" "docs" "$OUT"

# ── Case 4: test-suffixed filename in a non-test dir is still excluded ──────

OUT=$(derive "shared/src/commonMain/kotlin/FooTests.kt")
assert_eq "Tests.kt filename excluded even outside a test source set" "" "$OUT"

# ── Case 5: non-Compose, non-platform source still counts as code ──────────

OUT=$(derive "shared/src/commonMain/kotlin/Repository.kt")
assert_eq "commonMain source counts as code, not ui" "code" "$OUT"

# ── Summary ───────────────────────────────────────────────────────────────────

TOTAL=$((PASS + FAIL))
echo "Results: $PASS/$TOTAL passed"
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
