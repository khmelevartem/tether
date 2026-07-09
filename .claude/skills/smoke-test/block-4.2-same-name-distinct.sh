#!/usr/bin/env bash
set -euo pipefail

# Requires: block-4 already executed — three CLIs (SmokeMacA, SmokeMacB, third reusing "SmokeMacA")
# are alive. macOS mDNSResponder canonicalises the duplicate as `SmokeMacA (2)`.
#
# Regression guard for #368: same-base-name instances must stay distinguishable BY NAME. The
# mDNS-canonical `(N)` suffix recorded on discovery must survive the subsequent /hello exchange
# (which carries each sender's raw configured name) — it must not collapse two peers to one name.
#
# Assertion: in each CLI's last [peers] line, no two peers share the same display name.
# (block-4.1 already guarantees no two share host:port, so a duplicate name here = the #368 collapse.)

. "$(dirname "${BASH_SOURCE[0]}")/smoke-env.sh"

dup_names() {
    local log="$1"
    local last
    last=$(grep -aE "\[peers\]" "$log" 2>/dev/null | tail -1 || true)
    [ -z "$last" ] && { echo 0; return; }
    # Scope to our own SmokeMac* instances — the host's mDNS namespace may carry unrelated peers
    # from a concurrent run; only the SmokeMac instances are under this test's control.
    echo "${last#*\[peers\] }" \
        | tr ',' '\n' \
        | sed -e 's/@[^@]*$//' -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' \
        | { grep -E '^SmokeMac' || true; } \
        | sort \
        | uniq -d \
        | wc -l \
        | tr -d ' '
}

# Allow up to 20s for /hello to land after discovery (this is exactly when the bug used to bite).
for i in $(seq 1 20); do
    echo "list" > "$FIFO_A" &
    echo "list" > "$FIFO_B" &
    echo "list" > "$FIFO_C" &
    sleep 1
    A=$(dup_names "$LOG_A")
    B=$(dup_names "$LOG_B")
    C=$(dup_names "$LOG_C")
    [ "$A" -eq 0 ] && [ "$B" -eq 0 ] && [ "$C" -eq 0 ] && break
done

if [ "$A" -eq 0 ] && [ "$B" -eq 0 ] && [ "$C" -eq 0 ]; then
    echo "PASS: same-name distinct — no duplicate display names in any [peers] list"
else
    echo "FAIL: same-name distinct — A=$A B=$B C=$C duplicate names (need 0 each)"
    echo "Last [peers] lines:"
    grep -aE "\[peers\]" "$LOG_A" 2>/dev/null | tail -1
    grep -aE "\[peers\]" "$LOG_B" 2>/dev/null | tail -1
    grep -aE "\[peers\]" "$LOG_C" 2>/dev/null | tail -1
fi
