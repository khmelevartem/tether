#!/usr/bin/env bash
set -euo pipefail

# Always run this block — even after earlier FAILs.

. "$(dirname "${BASH_SOURCE[0]}")/smoke-env.sh"

UDID="${UDID:-$(cat "$IOS_UDID_FILE" 2>/dev/null)}"

set +e

echo "quit" > "$FIFO_A" 2>/dev/null || true
echo "quit" > "$FIFO_B" 2>/dev/null || true
echo "quit" > "$FIFO_C" 2>/dev/null || true
sleep 2

smoke_kill_instances

rm -rf "$SMOKE_DIR" 2>/dev/null

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
