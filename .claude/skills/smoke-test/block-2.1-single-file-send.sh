#!/usr/bin/env bash
set -euo pipefail

# Requires: block-1 already executed (CLI A alive at $LOG_A / $JPID_A / fifo /tmp/smoke-cliA-in).
# This script starts CLI B and waits for mutual discovery before the send scenario.
# block-2.2 and block-2.3 assume CLI B is still alive after this script.

LOG_A="${LOG_A:-/tmp/smoke-cliA.log}"
# Isolated home (see block-1): B is a distinct identity from A, so pairing is a real
# two-key first encounter. Downloads land under this home, not the user's real ~/Downloads.
HOME_B="${HOME_B:-/tmp/smoke-tether-B}"
DOWNLOADS_B="${DOWNLOADS_B:-$HOME_B/Downloads/Tether}"
JAR="${JAR:-$(ls "$(git rev-parse --show-toplevel)"/composeApp/build/libs/tether-cli-*.jar \
  "$(git rev-parse --show-toplevel)"/composeApp/build/libs/tether-cli.jar 2>/dev/null | head -1 || true)}"

LOG_B=/tmp/smoke-cliB.log
rm -f /tmp/smoke-cliB-in
mkfifo /tmp/smoke-cliB-in
sleep 600 > /tmp/smoke-cliB-in &
KEEPER_B=$!; disown $KEEPER_B
echo $KEEPER_B > /tmp/smoke-cliB-keeper.pid

nohup java -Duser.home="$HOME_B" -jar "$JAR" --name SmokeMacB --port 0 < /tmp/smoke-cliB-in > "$LOG_B" 2>&1 &
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

# Pairing: on first encounter the CLI pairing handler and the command loop both
# read from System.in.  The command loop holds the BufferedReader lock first, so
# the first "y" is consumed as an unknown command.  A second "y" reaches the
# handler.  We poll for the "[pair] Confirm?" prompt and send two "y" lines with
# a short delay so the lock handoff has time to complete.
confirm_pairing() {
  local log="$1" fifo="$2" label="$3"
  for j in $(seq 1 20); do
    set +e; grep -q '\[pair\] Confirm' "$log" 2>/dev/null; RC=$?; set -e
    if [ $RC -eq 0 ]; then
      echo "y" > "$fifo"   # may be eaten by command loop
      sleep 0.3
      echo "y" > "$fifo"   # reaches handler after lock handoff
      echo "  [smoke] pairing confirmation sent to $label"
      return 0
    fi
    sleep 1
  done
  echo "  [smoke] WARN: pairing prompt not seen in $label within 20s"
  return 1
}

# B confirms first (server side); then A confirms (client side).
confirm_pairing "$LOG_B" /tmp/smoke-cliB-in "B (server)" || true
confirm_pairing "$LOG_A" /tmp/smoke-cliA-in "A (client)" || true

for i in $(seq 1 30); do
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
export SEND1_NAME SEND1_SRC LOG_B JPID_B HOME_B DOWNLOADS_B
