#!/usr/bin/env bash
set -euo pipefail

# Requires: block-2 and block-3.1 already executed (CLI A and B alive).

. "$(dirname "${BASH_SOURCE[0]}")/smoke-env.sh"

rm -f "$FIFO_C"
mkfifo "$FIFO_C"
sleep 600 > "$FIFO_C" 2>/dev/null &
KEEPER_PID=$!; disown $KEEPER_PID
echo $KEEPER_PID > "$KEEPER_C"

TETHER_LOG_DEBUG=true nohup java -jar "$JAR" --name SmokeMacA --port 0 < "$FIFO_C" > "$LOG_C" 2>&1 &
JPID_C=$!; disown $JPID_C
echo $JPID_C > "$PID_C"

for i in $(seq 1 20); do
  echo "list" > "$FIFO_A" &
  echo "list" > "$FIFO_B" &
  echo "list" > "$FIFO_C" &
  sleep 1
  set +e
  A_OK=$(grep -aE "\[peers\]" "$LOG_A" 2>/dev/null | tail -1 | grep -oE 'SmokeMac[A-Z][^@]*' | sort -u | wc -l | tr -d ' ')
  B_OK=$(grep -aE "\[peers\]" "$LOG_B" 2>/dev/null | tail -1 | grep -oE 'SmokeMac[A-Z][^@]*' | sort -u | wc -l | tr -d ' ')
  C_OK=$(grep -aE "\[peers\]" "$LOG_C" 2>/dev/null | tail -1 | grep -oE 'SmokeMac[A-Z][^@]*' | sort -u | wc -l | tr -d ' ')
  set -e
  A_OK="${A_OK:-0}"; B_OK="${B_OK:-0}"; C_OK="${C_OK:-0}"
  [ "$A_OK" -ge 2 ] && [ "$B_OK" -ge 2 ] && [ "$C_OK" -ge 2 ] && break
done

if [ "$A_OK" -ge 2 ] && [ "$B_OK" -ge 2 ] && [ "$C_OK" -ge 2 ]; then
  echo "PASS: same-name discovery — each instance sees ≥2 SmokeMac peers"
else
  echo "FAIL: same-name discovery — A=$A_OK B=$B_OK C=$C_OK (need ≥2 each)"
  echo "Last [peers] lines:"
  set +e
  grep -aE "\[peers\]" "$LOG_A" 2>/dev/null | tail -1
  grep -aE "\[peers\]" "$LOG_B" 2>/dev/null | tail -1
  grep -aE "\[peers\]" "$LOG_C" 2>/dev/null | tail -1
  set -e
fi

export LOG_C JPID_C
