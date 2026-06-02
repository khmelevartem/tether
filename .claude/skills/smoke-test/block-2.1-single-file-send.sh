#!/usr/bin/env bash
set -euo pipefail

# Requires: block-1 already executed (CLI A alive at $LOG_A / $JPID_A / fifo /tmp/smoke-cliA-in).
# This script starts CLI B and waits for mutual discovery before the send scenario.
# block-2.2 and block-2.3 assume CLI B is still alive after this script.

LOG_A="${LOG_A:-/tmp/smoke-cliA.log}"
DOWNLOADS_B="${DOWNLOADS_B:-$HOME/Downloads/Tether}"
JAR="${JAR:-$(ls "$(git rev-parse --show-toplevel)"/composeApp/build/libs/tether-cli-*.jar \
  "$(git rev-parse --show-toplevel)"/composeApp/build/libs/tether-cli.jar 2>/dev/null | head -1)}"

LOG_B=/tmp/smoke-cliB.log
rm -f /tmp/smoke-cliB-in
mkfifo /tmp/smoke-cliB-in
sleep 600 > /tmp/smoke-cliB-in &
KEEPER_B=$!; disown $KEEPER_B
echo $KEEPER_B > /tmp/smoke-cliB-keeper.pid

nohup java -jar "$JAR" --name SmokeMacB --port 0 < /tmp/smoke-cliB-in > "$LOG_B" 2>&1 &
JPID_B=$!; disown $JPID_B
echo $JPID_B > /tmp/smoke-cliB.pid

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
SEND1_NAME="smoke-send-$(date +%s).txt"
SEND1_SRC="/tmp/$SEND1_NAME"
echo "send-via-cli-$(date +%s)" > "$SEND1_SRC"
echo "send SmokeMacB $SEND1_SRC" > /tmp/smoke-cliA-in &

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
