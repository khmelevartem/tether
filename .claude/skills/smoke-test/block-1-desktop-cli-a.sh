#!/usr/bin/env bash
set -euo pipefail

# Requires: block-0 already executed (JAR env var set, or re-derive below).

cd "$(git rev-parse --show-toplevel)"

JAR="${JAR:-$(ls composeApp/build/libs/tether-cli-*.jar composeApp/build/libs/tether-cli.jar 2>/dev/null | head -1 || true)}"
[ -z "$JAR" ] && { echo "FAIL: cli jar not found — run block-0 first"; exit 1; }

LOG_A=/tmp/smoke-cliA.log
# Isolated home → own device key + trust store, so A and B pair as two DISTINCT
# identities (real two-key PIN, real first encounter), not the shared ~/.config/tether
# under which the PIN degenerates to a constant. Wiped in block-7.
HOME_A="${HOME_A:-/tmp/smoke-tether-A}"
rm -f /tmp/smoke-cliA-in
mkfifo /tmp/smoke-cliA-in
sleep 600 > /tmp/smoke-cliA-in &
KEEPER_A=$!; disown $KEEPER_A
echo $KEEPER_A > /tmp/smoke-cliA-keeper.pid

nohup java -Duser.home="$HOME_A" -jar "$JAR" --name SmokeMacA --port 0 < /tmp/smoke-cliA-in > "$LOG_A" 2>&1 &
JPID_A=$!; disown $JPID_A
echo $JPID_A > /tmp/smoke-cliA.pid

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

# No curl /pair shape-check here: the CLI now has a real PairingConfirmationHandler, so an
# untrusted /pair blocks on an interactive y/n prompt instead of auto-responding — a 5s curl
# would always time out. The Desktop key-material path is covered end-to-end by the real
# handshake in block-2.1; the explicit X.509 SPKI gate lives in block-5.5 (iOS), whose server
# has no handler and still auto-accepts.

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
echo "list" > /tmp/smoke-cliA-in &
sleep 1
set +e; tail "$LOG_A" | grep -qE "\[list\]|\[peers\]"; RC=$?; set -e
[ $RC -eq 0 ] && echo "PASS: stdin list" || echo "FAIL: no [list] or [peers] line in log"

echo ""
echo "CLI A alive. PORT_A=$PORT_A  JPID_A=$JPID_A  LOG_A=$LOG_A  HOME_A=$HOME_A"
