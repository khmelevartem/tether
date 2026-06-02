#!/usr/bin/env bash
set -euo pipefail

# Requires: block-3 already executed — three CLIs (SmokeMacA, SmokeMacB, third "SmokeMacA")
# are alive. macOS mDNSResponder will canonicalise the duplicate as `SmokeMacA (2)`.
#
# Regression guard for #346: the same physical peer must collapse to a single entry in every
# observer's [peers] list.
#
# Assertion: in each CLI's last [peers] line, no two peers share the same host:port.

LOG_A="${LOG_A:-/tmp/smoke-cliA.log}"
LOG_B="${LOG_B:-/tmp/smoke-cliB.log}"
LOG_C="${LOG_C:-/tmp/smoke-cliC.log}"

collisions() {
    local log="$1"
    local last
    last=$(grep -aE "\[peers\]" "$log" 2>/dev/null | tail -1 || true)
    [ -z "$last" ] && { echo 0; return; }
    echo "$last" \
        | { grep -oE '[^ ,]+@[^ ,]+' || true; } \
        | sed 's/.*@//' \
        | sort \
        | uniq -d \
        | wc -l \
        | tr -d ' '
}

# Give peers up to 20s to converge after the third CLI joined.
for i in $(seq 1 20); do
    echo "list" > /tmp/smoke-cliA-in &
    echo "list" > /tmp/smoke-cliB-in &
    echo "list" > /tmp/smoke-cliC-in &
    sleep 1
    A=$(collisions "$LOG_A")
    B=$(collisions "$LOG_B")
    C=$(collisions "$LOG_C")
    [ "$A" -eq 0 ] && [ "$B" -eq 0 ] && [ "$C" -eq 0 ] && break
done

if [ "$A" -eq 0 ] && [ "$B" -eq 0 ] && [ "$C" -eq 0 ]; then
    echo "PASS: peer dedup — no host:port collisions in any [peers] list"
else
    echo "FAIL: peer dedup — A=$A B=$B C=$C host:port collisions (need 0 each)"
    echo "Last [peers] lines:"
    grep -aE "\[peers\]" "$LOG_A" 2>/dev/null | tail -1
    grep -aE "\[peers\]" "$LOG_B" 2>/dev/null | tail -1
    grep -aE "\[peers\]" "$LOG_C" 2>/dev/null | tail -1
fi
