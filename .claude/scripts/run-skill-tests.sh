#!/usr/bin/env bash
# Single entry point for every skill test. Discovers and runs all `*.test.sh`
# under .claude/skills/, so a new test file anywhere in there is picked up with
# no wiring change. CI and local both call just this.
# Exit non-zero if any test file fails.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SKILLS_DIR="$REPO_ROOT/.claude/skills"

FAILED=0
TOTAL=0

while IFS= read -r test_file; do
  TOTAL=$((TOTAL + 1))
  echo "══ ${test_file#"$REPO_ROOT/"} ══"
  if ! bash "$test_file"; then
    FAILED=$((FAILED + 1))
  fi
  echo
done < <(find "$SKILLS_DIR" -name '*.test.sh' | sort)

if [ "$TOTAL" -eq 0 ]; then
  echo "No skill tests found under ${SKILLS_DIR#"$REPO_ROOT/"}."
  exit 0
fi

if [ "$FAILED" -eq 0 ]; then
  echo "✅ All $TOTAL skill test file(s) passed."
else
  echo "❌ $FAILED of $TOTAL skill test file(s) failed."
  exit 1
fi
