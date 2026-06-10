#!/usr/bin/env bash
set -euo pipefail

# Requires: block-0 already executed (cli jar built).

. "$(dirname "${BASH_SOURCE[0]}")/smoke-env.sh"

cd "$TETHER_ROOT"

[ -z "$JAR" ] && { echo "FAIL: cli jar not found — run block-0 first"; exit 1; }

rm -f "$FIFO_A"
mkfifo "$FIFO_A"
sleep 600 > "$FIFO_A" 2>/dev/null &
KEEPER_PID=$!; disown $KEEPER_PID
echo $KEEPER_PID > "$KEEPER_A"

# Wrap the JVM in a subshell that records its real exit code — block-6 reads $EXIT_A rather than
# `wait` on this disowned PID, which returns 127 from another shell.
rm -f "$EXIT_A"
( TETHER_LOG_DEBUG=true java -jar "$JAR" --name SmokeMacA --port 0 < "$FIFO_A" > "$LOG_A" 2>&1; echo $? > "$EXIT_A" ) &
JPID_A=$!; disown $JPID_A
echo $JPID_A > "$PID_A"

echo "Waiting for FileServer to start..."
for i in $(seq 1 30); do
  set +e; grep -q 'FileServer started' "$LOG_A" 2>/dev/null; RC=$?; set -e
  [ $RC -eq 0 ] && break
  sleep 1
done

set +e; grep -q 'FileServer started' "$LOG_A" 2>/dev/null; RC=$?; set -e
[ $RC -ne 0 ] && { echo "FAIL: FileServer did not start within 30s"; exit 1; }

PORT_A=$(grep -oE 'port[[:space:]]*:[[:space:]]*[0-9]+' "$LOG_A" | grep -oE '[0-9]+' | head -1)
echo "PASS: startup — pid=$JPID_A, port=$PORT_A"

# /health
HEALTH=$(curl -sf --max-time 5 "http://localhost:$PORT_A/health" || echo "FAIL")
[ "$HEALTH" = "Tether OK" ] && echo "PASS: /health — $HEALTH" || echo "FAIL: /health returned: $HEALTH"

# /pair — X.509 EC P-256 SubjectPublicKeyInfo shape check
PAIR_RESP=$(curl -sf --max-time 5 -X POST "http://localhost:$PORT_A/pair" \
  -H "Content-Type: application/json" \
  -d '{"publicKey":[1,2,3], "deviceName":"smoke"}' || echo "")
set +e
echo "$PAIR_RESP" | jq -e '.publicKey | length == 91 and .[0] == 48 and .[26] == 4' > /dev/null 2>&1
RC=$?
set -e
[ $RC -eq 0 ] && echo "PASS: /pair X.509 EC P-256 SubjectPublicKeyInfo" || echo "FAIL: bad publicKey shape: $PAIR_RESP"

# Port LISTEN
set +e; lsof -nP -iTCP:"$PORT_A" 2>/dev/null | head -3; set -e
echo "PASS: port LISTEN check printed above"

# mDNS publish (log)
set +e; grep -q "mDNS started" "$LOG_A" 2>/dev/null; RC=$?; set -e
[ $RC -eq 0 ] && echo "PASS: mDNS publish (log)" || echo "FAIL: mDNS started not in log"

# mDNS publish (dns-sd, optional)
if command -v dns-sd >/dev/null 2>&1; then
  set +e
  DNS_RESULT=$( ( dns-sd -B _tether._tcp. local. 2>&1 & DNSSD_PID=$!; sleep 8; kill $DNSSD_PID 2>/dev/null ) | grep SmokeMacA | head -1 )
  set -e
  [ -n "$DNS_RESULT" ] && echo "PASS: mDNS publish (dns-sd) — $DNS_RESULT" || echo "FAIL: SmokeMacA not seen via dns-sd"
else
  echo "SKIP: mDNS publish (dns-sd) — dns-sd not available"
fi

# stdin list
echo "list" > "$FIFO_A" &
sleep 1
set +e; tail "$LOG_A" | grep -qE "\[list\]|\[peers\]"; RC=$?; set -e
[ $RC -eq 0 ] && echo "PASS: stdin list" || echo "FAIL: no [list] or [peers] line in log"

echo ""
echo "CLI A alive. PORT_A=$PORT_A  JPID_A=$JPID_A  LOG_A=$LOG_A"
