#!/usr/bin/env bash
set -euo pipefail

# Requires: block-1 already executed (CLI A alive at $LOG_A / fifo $FIFO_A).
# This script starts CLI B and waits for mutual discovery before the send scenario.
# block-2.2 and block-2.3 assume CLI B is still alive after this script.

. "$(dirname "${BASH_SOURCE[0]}")/smoke-env.sh"

DOWNLOADS_B="${DOWNLOADS_B:-$HOME/Downloads/Tether}"

rm -f "$FIFO_B"
mkfifo "$FIFO_B"
sleep 600 > "$FIFO_B" &
KEEPER_PID=$!; disown $KEEPER_PID
echo $KEEPER_PID > "$KEEPER_B"

TETHER_LOG_DEBUG=true nohup java -jar "$JAR" --name SmokeMacB --port 0 < "$FIFO_B" > "$LOG_B" 2>&1 &
JPID_B=$!; disown $JPID_B
echo $JPID_B > "$PID_B"

echo "Waiting for mutual mDNS discovery..."
for i in $(seq 1 30); do
  set +e
  grep -q 'SmokeMacA' "$LOG_B" 2>/dev/null && grep -q 'SmokeMacB' "$LOG_A" 2>/dev/null
  RC=$?
  set -e
  [ $RC -eq 0 ] && break
  sleep 1
done

# Single-file send scenario
SEND1_NAME="${SMOKE_SEND_PREFIX}-send-$(date +%s).txt"
SEND1_SRC="$SMOKE_DIR/$SEND1_NAME"
echo "send-via-cli-$(date +%s)" > "$SEND1_SRC"
echo "send SmokeMacB $SEND1_SRC" > "$FIFO_A" &

for i in $(seq 1 15); do
  set +e; grep -qE "^\[send\] (done|partial|error)" "$LOG_A" 2>/dev/null; RC=$?; set -e
  [ $RC -eq 0 ] && break
  sleep 1
done

set +e
grep -qE "^\[send\] done" "$LOG_A" 2>/dev/null && \
  [ -f "$DOWNLOADS_B/$SEND1_NAME" ] && \
  diff "$SEND1_SRC" "$DOWNLOADS_B/$SEND1_NAME" >/dev/null 2>&1
RC=$?
set -e

[ $RC -eq 0 ] && echo "PASS: single-file send — $SEND1_NAME byte-identical" || echo "FAIL: single-file send"
export SEND1_NAME SEND1_SRC LOG_B JPID_B
