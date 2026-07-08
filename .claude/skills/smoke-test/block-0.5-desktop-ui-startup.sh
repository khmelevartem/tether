#!/usr/bin/env bash
set -euo pipefail

# Requires: nothing beyond a clean worktree — `./gradlew :composeApp:run` builds and launches
# the Compose UI itself. Independent of the CLI jar block-0 builds and of any CLI instance.
#
# This launches the Compose UI (not the CLI) — narrow exception to the skill's general
# "don't use :composeApp:run" rule (see SKILL.md "What NOT to do"), scoped to asserting the
# startup log carries no main-thread violation. It is not a substitute for CLI-functional
# coverage (send, discovery, etc.), which stays on the cli jar blocks.

. "$(dirname "${BASH_SOURCE[0]}")/smoke-env.sh"

cd "$TETHER_ROOT"

UI_LOG="$SMOKE_DIR/desktop-ui.log"

( ./gradlew :composeApp:run -q > "$UI_LOG" 2>&1 ) >/dev/null 2>&1 &
disown $!

sleep 8

VIOLATIONS=$(grep -c 'NotOnMainThreadException' "$UI_LOG" 2>/dev/null || true)
VIOLATIONS="${VIOLATIONS:-0}"
[ "$VIOLATIONS" = "0" ] && echo "PASS: no NotOnMainThreadException in startup log" \
  || echo "FAIL: NotOnMainThreadException found in startup log ($UI_LOG)"

# The launched JVM's main class (com.tubetoast.tether.MainUiKt), not the gradle wrapper
# invocation, is the reliable kill target — no CLI quit/FIFO exists for the UI.
pkill -9 -f "com.tubetoast.tether.MainUiKt" 2>/dev/null
pkill -9 -f "gradlew.*:composeApp:run" 2>/dev/null
true
