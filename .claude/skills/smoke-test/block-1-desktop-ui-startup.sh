#!/usr/bin/env bash
set -euo pipefail

# Requires: nothing beyond a clean worktree — `./gradlew :composeApp:run` builds and launches
# the Compose UI itself. Independent of the CLI jar block-0 builds and of any CLI instance.
#
# This launches the Compose UI (not the CLI) — narrow exception to the skill's general
# "don't use :composeApp:run" rule (see SKILL.md "What NOT to do"), scoped to asserting the
# startup log shows a successful launch with no stack trace. It is not a substitute for
# CLI-functional coverage (send, discovery, etc.), which stays on the cli jar blocks. It
# complements the desktopTest regression test (RootComponentMainThreadTest), which is the
# primary, fast guard — this is a coarse, end-to-end check through the real launch path.

. "$(dirname "${BASH_SOURCE[0]}")/smoke-env.sh"

cd "$TETHER_ROOT"

# The gradlew client hands the actual run off to the (daemon-cached) Gradle build; the launched
# UI JVM is not its child, so it is resolved below by its worktree-unique classpath entry rather
# than by PID lineage — same worktree-scoped-match idiom smoke_kill_instances uses for $SMOKE_JAR,
# in place of a bare class-name pattern that would also match another worktree's UI process.
( ./gradlew :composeApp:run -q > "$UI_LOG" 2>&1 ) >/dev/null 2>&1 &
disown $! 2>/dev/null

UI_JAR="$TETHER_ROOT/composeApp/build/libs/composeApp-desktop.jar"

echo "Waiting for Desktop UI to start..."
for i in $(seq 1 60); do
  set +e; grep -q 'HealthMonitor: started' "$UI_LOG" 2>/dev/null; RC=$?; set -e
  [ $RC -eq 0 ] && break
  sleep 1
done

UI_PID=$(pgrep -f "$UI_JAR" 2>/dev/null | head -1 || true)
[ -n "$UI_PID" ] && echo "$UI_PID" > "$PID_UI"

set +e; grep -q 'HealthMonitor: started' "$UI_LOG" 2>/dev/null; RC=$?; set -e
if [ $RC -ne 0 ]; then
  echo "FAIL: Desktop UI did not reach startup (FileServer + HealthMonitor) within 60s ($UI_LOG)"
else
  echo "PASS: Desktop UI starts successfully (FileServer + HealthMonitor started)"
fi

# Decompose's default error handler calls printStackTrace() on ANY main-thread violation,
# regardless of exception class — a generic stack-frame-shape check (rather than one
# exception name) catches this whole class of regression, of which #421 is one instance.
STACK_FRAMES=$(grep -cE '^[[:space:]]*at ' "$UI_LOG" 2>/dev/null || true)
STACK_FRAMES="${STACK_FRAMES:-0}"
[ "$STACK_FRAMES" = "0" ] && echo "PASS: no startup exception (no stack trace in log)" \
  || echo "FAIL: stack trace found in startup log ($UI_LOG) — see #421 for this regression class"

[ -n "$UI_PID" ] && smoke_kill_tree "$UI_PID"
true
