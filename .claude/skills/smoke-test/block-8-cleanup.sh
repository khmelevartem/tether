#!/usr/bin/env bash
set -euo pipefail

# Always run this block — even after earlier FAILs.

. "$(dirname "${BASH_SOURCE[0]}")/smoke-env.sh"

set +e

# Capture UDID and the Desktop UI pid before smoke_reset removes the scratch dir. Kept under
# `set +e` because a missing ios.udid / desktop-ui.pid (any run without that block) must not
# abort cleanup.
UDID="${UDID:-$(cat "$IOS_UDID_FILE" 2>/dev/null)}"
UI_PID="$(cat "$PID_UI" 2>/dev/null)"

# Graceful quit to live CLIs. Each write runs in an fd-detached subshell so a writer blocked
# on a reader-less FIFO (already-dead CLI) cannot hold an inherited pipe open and wedge a
# caller that reads this block through a pipe; blocked writers are reaped after the grace wait.
qpids=""
for f in "$FIFO_A" "$FIFO_B" "$FIFO_C"; do
  ( echo "quit" > "$f" ) >/dev/null 2>&1 &
  qpids="$qpids $!"
  disown 2>/dev/null
done
sleep 2
kill $qpids 2>/dev/null

# Redundant guard for a Desktop UI process left behind by an interrupted block-1 hand-run —
# block-1 already kills its own tree on the normal path.
[ -n "$UI_PID" ] && smoke_kill_tree "$UI_PID"

# Backstop for a UI JVM that appears after block-1's readiness poll times out (e.g. a cold
# compile exceeding 60s), which leaves $UI_PID uncaptured — scoped by jar path like
# smoke_kill_instances scopes $SMOKE_JAR, so a concurrent run in another worktree is untouched.
pkill -9 -f "$UI_JAR" 2>/dev/null || true

smoke_reset

# Received files in the shared downloads dir are named with this run's prefix, so the glob
# targets only this run and leaves a concurrent run's downloads intact.
rm -f "$DOWNLOADS_B/${SMOKE_SEND_PREFIX}-"*.txt 2>/dev/null

adb shell rm -f "/sdcard/Android/data/com.tubetoast.tether/files/Tether/${SMOKE_SEND_PREFIX}-android"*.txt 2>/dev/null
adb shell am force-stop com.tubetoast.tether 2>/dev/null

if [ -n "$UDID" ]; then
  xcrun simctl terminate "$UDID" com.tubetoast.tether.Tether 2>/dev/null || true
fi
# iOS build/launch logs live under $SMOKE_DIR, already removed above.

set -e
echo "Cleanup done."
