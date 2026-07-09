#!/usr/bin/env bash
set -euo pipefail

# Requires: block-2 already executed (CLI A alive, pid in $PID_A).
# After block-3.3 retry scenario the last result was AllSent → expected exit code 0.

. "$(dirname "${BASH_SOURCE[0]}")/smoke-env.sh"

JPID_A="${JPID_A:-$(cat "$PID_A" 2>/dev/null)}"
[ -z "$JPID_A" ] && { echo "FAIL: JPID_A unknown — cannot check graceful quit"; exit 1; }

echo "quit" > "$FIFO_A" &

EXITED=0
for i in $(seq 1 8); do
  set +e; ps -p "$JPID_A" > /dev/null 2>&1; ALIVE=$?; set -e
  if [ $ALIVE -ne 0 ]; then
    EXITED=1
    break
  fi
  sleep 1
done

if [ $EXITED -eq 0 ]; then
  echo "FAIL: CLI A did not exit gracefully within 8s — killing"
  set +e; kill -9 "$JPID_A" 2>/dev/null; set -e
  echo "SKIP: exit-code check (process had to be force-killed)"
else
  echo "PASS: graceful quit — exited"
  # Exit code is written by the launch wrapper in block-2; wait on a detached PID returns 127.
  EXIT_FILE_READY=0
  for i in $(seq 1 5); do
    [ -f "$EXIT_A" ] && { EXIT_FILE_READY=1; break; }
    sleep 1
  done
  if [ $EXIT_FILE_READY -eq 0 ]; then
    echo "FAIL: $EXIT_A never written — exit code unknown"
  else
    EXIT_CODE=$(cat "$EXIT_A")
    [ "$EXIT_CODE" = "0" ] && echo "PASS: exit code — exit=$EXIT_CODE (last send AllSent)" || echo "FAIL: exit code — exit=$EXIT_CODE"
  fi
fi
