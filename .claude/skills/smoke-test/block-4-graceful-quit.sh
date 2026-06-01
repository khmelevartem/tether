#!/usr/bin/env bash
set -euo pipefail

# Requires: block-1 already executed (CLI A alive, $JPID_A known / /tmp/smoke-cliA.pid written).
# After block-2 retry scenario the last result was AllSent → expected exit code 0.

JPID_A="${JPID_A:-$(cat /tmp/smoke-cliA.pid 2>/dev/null)}"
[ -z "$JPID_A" ] && { echo "FAIL: JPID_A unknown — cannot check graceful quit"; exit 1; }

echo "quit" > /tmp/smoke-cliA-in &

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
  set +e; wait "$JPID_A" 2>/dev/null; EXIT_A=$?; set -e
  echo "PASS: graceful quit — exited"
  [ "$EXIT_A" = "0" ] && echo "PASS: exit code — exit=$EXIT_A (last send AllSent)" || echo "FAIL: exit code — exit=$EXIT_A"
fi
