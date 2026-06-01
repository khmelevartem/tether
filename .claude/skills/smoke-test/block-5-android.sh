#!/usr/bin/env bash
set -euo pipefail

# Requires: block-1 already executed (CLI A alive, LOG_A set or /tmp/smoke-cliA.log present).

LOG_A="${LOG_A:-/tmp/smoke-cliA.log}"

cd "$(git rev-parse --show-toplevel)"

ADB_DEVICE=$(adb devices 2>/dev/null | awk '/device$/ && !/List/ {print $1}' | head -1)
if [ -z "$ADB_DEVICE" ]; then
  echo "SKIP: no adb device connected"
  exit 0
fi
echo "adb device: $ADB_DEVICE"

./gradlew -q :composeApp:installDebug
echo "PASS: install"

adb logcat -c

adb shell am start -n com.tubetoast.tether/.MainActivity

DEADLINE=$(($(date +%s) + 12))
while [ "$(date +%s)" -lt "$DEADLINE" ]; do
  set +e; adb logcat -d 2>/dev/null | grep -q "NSD service registered"; RC=$?; set -e
  [ $RC -eq 0 ] && break
  sleep 1
done
NSD_READY_MS=$(python3 -c "import time; print(int(time.time() * 1000))")

ANDROID_PORT=$(adb logcat -d 2>/dev/null | grep "FileServer started on port" | grep -oE '[0-9]+$' | tail -1)
echo "Android port: $ANDROID_PORT"

ANDROID_IP=$(adb shell ip addr show wlan0 2>&1 | grep "inet " | awk '{print $2}' | cut -d/ -f1 | head -1)
echo "Android IP: $ANDROID_IP"

if echo "$ANDROID_IP" | grep -q "^10\.0\.2\."; then
  echo "INFO: emulator detected — /health via adb forward"
  adb forward "tcp:18080" "tcp:$ANDROID_PORT"
  HEALTH_HOST="localhost:18080"
else
  HEALTH_HOST="$ANDROID_IP:$ANDROID_PORT"
fi

set +e
HEALTH=$(curl -sf --max-time 5 "http://$HEALTH_HOST/health" || echo "FAIL")
set -e
[ "$HEALTH" = "Tether OK" ] && echo "PASS: /health — $HEALTH" || echo "FAIL: /health — $HEALTH"

# Cross-discovery with timing
for i in $(seq 1 30); do
  set +e; grep -aE "\[peers\] .*@${ANDROID_IP}:" "$LOG_A" 2>/dev/null | tail -1 | grep -q .; RC=$?; set -e
  [ $RC -eq 0 ] && break
  sleep 1
done
NOW_MS=$(python3 -c "import time; print(int(time.time() * 1000))")
DELTA_MS=$((NOW_MS - NSD_READY_MS))
set +e
ANDROID_NAME=$(grep -aE "\[peers\]" "$LOG_A" 2>/dev/null | tail -1 | grep -oE "[^, ]+@${ANDROID_IP}" | head -1 | sed 's/@.*//')
set -e
echo "cross-discovery: ${DELTA_MS}ms, peer=${ANDROID_NAME:-unknown}"
[ -n "$ANDROID_NAME" ] && echo "PASS: cross-discovery" || echo "FAIL: Android peer not seen on Desktop"

# Send Desktop → Android
if echo "$ANDROID_IP" | grep -q "^10\.0\.2\."; then
  echo "SKIP: send Desktop→Android — QEMU user-mode NAT drops host→guest TCP payload; see docs/knowledge/android-emulator-networking.md"
elif [ -z "$ANDROID_NAME" ]; then
  echo "SKIP: send Desktop→Android — cross-discovery did not surface Android peer"
else
  ANDROID_NAME_FILE="smoke-android-$(date +%s).txt"
  ANDROID_SRC="/tmp/$ANDROID_NAME_FILE"
  echo "send-to-android-$(date +%s)" > "$ANDROID_SRC"
  set +e; PREV_DONE=$(grep -cE "^\[send\] done" "$LOG_A" 2>/dev/null || echo 0); set -e
  echo "send $ANDROID_NAME $ANDROID_SRC" > /tmp/smoke-cliA-in &
  for i in $(seq 1 15); do
    set +e; NOW_DONE=$(grep -cE "^\[send\] done" "$LOG_A" 2>/dev/null || echo 0); set -e
    [ "$NOW_DONE" -gt "$PREV_DONE" ] && break
    sleep 1
  done
  set +e
  ANDROID_DEST=$(adb shell run-as com.tubetoast.tether ls -1 \
    "/data/data/com.tubetoast.tether/files/Tether/$ANDROID_NAME_FILE" 2>/dev/null \
    || adb shell ls -1 "/sdcard/Android/data/com.tubetoast.tether/files/Tether/$ANDROID_NAME_FILE" 2>/dev/null)
  ANDROID_DEST=$(echo "$ANDROID_DEST" | tr -d '\r' | head -1)
  set -e
  if [ -n "$ANDROID_DEST" ]; then
    set +e; adb shell cat "$ANDROID_DEST" 2>/dev/null | diff - "$ANDROID_SRC"; RC=$?; set -e
    [ $RC -eq 0 ] && echo "PASS: send Desktop→Android" || echo "FAIL: send Desktop→Android — diff mismatch"
  else
    echo "FAIL: send Desktop→Android — file not found on device"
  fi
fi

adb shell am force-stop com.tubetoast.tether
echo "PASS: force-stop"
