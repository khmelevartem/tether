#!/usr/bin/env bash
set -euo pipefail

# Requires: block-1 already executed (CLI A alive at $LOG_A / $JPID_A / fifo /tmp/smoke-cliA-in).
# Requires: CLI B alive with name SmokeMacB.

LOG_A="${LOG_A:-/tmp/smoke-cliA.log}"
DOWNLOADS_B="${DOWNLOADS_B:-$HOME/Downloads/Tether}"

TS=$(date +%s)
M1="/tmp/smoke-multi-${TS}-1.txt"; echo "m1-$TS" > "$M1"
M2="/tmp/smoke-multi-${TS}-2.txt"; echo "m2-$TS" > "$M2"
M3="/tmp/smoke-multi-${TS}-3.txt"; echo "m3-$TS" > "$M3"

set +e
PREV_DONE=$(grep -cE "^\[send\] done" "$LOG_A" 2>/dev/null || echo 0)
set -e

echo "send SmokeMacB $M1 $M2 $M3" > /tmp/smoke-cliA-in &

for i in $(seq 1 20); do
  set +e; NOW_DONE=$(grep -cE "^\[send\] done" "$LOG_A" 2>/dev/null || echo 0); set -e
  [ "$NOW_DONE" -gt "$PREV_DONE" ] && break
  sleep 1
done

set +e
grep -qE '^\[send\] done — 3/3 sent' "$LOG_A" 2>/dev/null && \
  diff "$M1" "$DOWNLOADS_B/$(basename "$M1")" >/dev/null 2>&1 && \
  diff "$M2" "$DOWNLOADS_B/$(basename "$M2")" >/dev/null 2>&1 && \
  diff "$M3" "$DOWNLOADS_B/$(basename "$M3")" >/dev/null 2>&1
RC=$?
set -e

[ $RC -eq 0 ] && echo "PASS: multi-file send — 3/3 sent, all byte-identical" || echo "FAIL: multi-file send"
export M1 M2 M3
