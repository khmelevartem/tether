#!/usr/bin/env bash
set -euo pipefail

# Requires: block-1 already executed (CLI A alive at $LOG_A / $JPID_A / fifo /tmp/smoke-cliA-in).
# Requires: CLI B was alive (this script stops and restarts it).

LOG_A="${LOG_A:-/tmp/smoke-cliA.log}"
LOG_B="${LOG_B:-/tmp/smoke-cliB.log}"
DOWNLOADS_B="${DOWNLOADS_B:-$HOME/Downloads/Tether}"
JAR="${JAR:-$(ls "$(git rev-parse --show-toplevel)"/composeApp/build/libs/tether-cli-*.jar \
  "$(git rev-parse --show-toplevel)"/composeApp/build/libs/tether-cli.jar 2>/dev/null | head -1)}"

# Stop B to trigger an error on send.
set +e; kill "$(cat /tmp/smoke-cliB.pid 2>/dev/null)" 2>/dev/null; set -e
sleep 2

RETRY_NAME="smoke-retry-$(date +%s).txt"
RETRY_SRC="/tmp/$RETRY_NAME"
echo "retry-payload-$(date +%s)" > "$RETRY_SRC"

set +e; PREV_ERR=$(grep -cE "^\[send\] error" "$LOG_A" 2>/dev/null || echo 0); set -e
echo "send SmokeMacB $RETRY_SRC" > /tmp/smoke-cliA-in &

for i in $(seq 1 15); do
  set +e; NOW_ERR=$(grep -cE "^\[send\] error" "$LOG_A" 2>/dev/null || echo 0); set -e
  [ "$NOW_ERR" -gt "$PREV_ERR" ] && break
  sleep 1
done

# Restart B with the same name so the engine's PeerIdentity resolves again.
nohup java -jar "$JAR" --name SmokeMacB --port 0 < /tmp/smoke-cliB-in > "$LOG_B" 2>&1 &
JPID_B=$!; disown $JPID_B
echo $JPID_B > /tmp/smoke-cliB.pid

for i in $(seq 1 30); do
  set +e; grep -q 'SmokeMacB' "$LOG_A" 2>/dev/null; RC=$?; set -e
  [ $RC -eq 0 ] && break
  sleep 1
done

set +e; PREV_DONE=$(grep -cE "^\[send\] done" "$LOG_A" 2>/dev/null || echo 0); set -e
echo "retry SmokeMacB" > /tmp/smoke-cliA-in &

for i in $(seq 1 15); do
  set +e; NOW_DONE=$(grep -cE "^\[send\] done" "$LOG_A" 2>/dev/null || echo 0); set -e
  [ "$NOW_DONE" -gt "$PREV_DONE" ] && break
  sleep 1
done

set +e
[ -f "$DOWNLOADS_B/$RETRY_NAME" ] && diff "$RETRY_SRC" "$DOWNLOADS_B/$RETRY_NAME" >/dev/null 2>&1
RC=$?
set -e

[ $RC -eq 0 ] && echo "PASS: retry — file landed byte-identical after retry" || echo "FAIL: retry"
export RETRY_NAME RETRY_SRC
