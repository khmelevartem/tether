#!/usr/bin/env bash
set -euo pipefail

# Always run this block — even after earlier FAILs.

. "$(dirname "${BASH_SOURCE[0]}")/smoke-env.sh"

set +e

# Capture UDID before smoke_reset removes the scratch dir. Kept under `set +e` because a
# missing ios.udid (any run without the iOS block) must not abort cleanup.
UDID="${UDID:-$(cat "$IOS_UDID_FILE" 2>/dev/null)}"

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
