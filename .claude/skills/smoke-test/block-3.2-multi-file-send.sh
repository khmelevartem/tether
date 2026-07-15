#!/usr/bin/env bash
set -euo pipefail

# Requires: block-2 already executed (CLI A alive at $LOG_A / fifo $FIFO_A).
# Requires: CLI B alive with name SmokeMacB.

. "$(dirname "${BASH_SOURCE[0]}")/smoke-env.sh"

TS=$(date +%s)
M1="$SMOKE_DIR/${SMOKE_SEND_PREFIX}-multi-${TS}-1.txt"; echo "m1-$TS" > "$M1"
M2="$SMOKE_DIR/${SMOKE_SEND_PREFIX}-multi-${TS}-2.txt"; echo "m2-$TS" > "$M2"
M3="$SMOKE_DIR/${SMOKE_SEND_PREFIX}-multi-${TS}-3.txt"; echo "m3-$TS" > "$M3"

PREV_DONE=$(grep -cE "^\[send\] done" "$LOG_A" 2>/dev/null || true)
PREV_DONE=${PREV_DONE:-0}

echo "send SmokeMacB $M1 $M2 $M3" > "$FIFO_A" &

for i in $(seq 1 20); do
  NOW_DONE=$(grep -cE "^\[send\] done" "$LOG_A" 2>/dev/null || true)
  NOW_DONE=${NOW_DONE:-0}
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
