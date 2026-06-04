#!/usr/bin/env bash
set -euo pipefail

# Always run this block — even after earlier FAILs.

DOWNLOADS_B="${DOWNLOADS_B:-$HOME/Downloads/Tether}"

set +e

echo "quit" > /tmp/smoke-cliA-in 2>/dev/null || true
echo "quit" > /tmp/smoke-cliB-in 2>/dev/null || true
echo "quit" > /tmp/smoke-cliC-in 2>/dev/null || true
sleep 2

kill "$(cat /tmp/smoke-cliA.pid /tmp/smoke-cliB.pid /tmp/smoke-cliC.pid \
  /tmp/smoke-cliA-keeper.pid /tmp/smoke-cliB-keeper.pid /tmp/smoke-cliC-keeper.pid 2>/dev/null)" 2>/dev/null

pkill -f 'com.tubetoast.tether.*\.jar' 2>/dev/null

rm -f /tmp/smoke-cli*-in /tmp/smoke-cli*.log /tmp/smoke-cli*.pid /tmp/smoke-cli*-keeper.pid \
  /tmp/smoke-send.txt /tmp/smoke-android.txt

# Isolated per-instance homes (device keys + trust stores + downloads). Wiping them is what
# makes every run a deterministic first encounter — without this, pairing would be skipped
# on the second run because trust persisted.
rm -rf /tmp/smoke-tether-A /tmp/smoke-tether-B /tmp/smoke-tether-C

# Remove scratch files created by block-2 scenarios (by exported names if available, else glob).
for VAR in SEND1_NAME M1 M2 M3 RETRY_NAME; do
  FILE="${!VAR:-}"
  [ -n "$FILE" ] && rm -f "$DOWNLOADS_B/$FILE" "$DOWNLOADS_B/$(basename "$FILE")" 2>/dev/null
done
# Safety glob for any leftover smoke files in the downloads dir.
rm -f "$DOWNLOADS_B"/smoke-*.txt 2>/dev/null

adb shell rm -f /sdcard/Android/data/com.tubetoast.tether/files/Tether/smoke-android*.txt 2>/dev/null
adb shell am force-stop com.tubetoast.tether 2>/dev/null

UDID="${UDID:-}"
if [ -n "$UDID" ]; then
  xcrun simctl terminate "$UDID" com.tubetoast.tether.Tether 2>/dev/null || true
fi
rm -f /tmp/smoke-ios-build.log /tmp/smoke-ios-launch.log

set -e
echo "Cleanup done."
