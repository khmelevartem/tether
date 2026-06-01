#!/usr/bin/env bash
set -euo pipefail

# Requires: block-1 already executed (CLI A alive / fifo /tmp/smoke-cliA-in).
# Requires: CLI B alive at $LOG_B (needs to see the rename propagated via mDNS).

LOG_B="${LOG_B:-/tmp/smoke-cliB.log}"

echo "name RenamedA" > /tmp/smoke-cliA-in &

for i in $(seq 1 15); do
  set +e; grep -q "RenamedA" "$LOG_B" 2>/dev/null; RC=$?; set -e
  [ $RC -eq 0 ] && break
  sleep 1
done

set +e; grep -q "RenamedA" "$LOG_B" 2>/dev/null; RC=$?; set -e
[ $RC -eq 0 ] && echo "PASS: device name rename — B sees RenamedA within 15s" || echo "FAIL: RenamedA not seen in B's log"
