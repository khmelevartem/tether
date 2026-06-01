#!/usr/bin/env bash
set -euo pipefail

# Requires: block-1 and block-2 already executed (CLI A and B alive).

LOG_A="${LOG_A:-/tmp/smoke-cliA.log}"
LOG_B="${LOG_B:-/tmp/smoke-cliB.log}"
JAR="${JAR:-$(ls "$(git rev-parse --show-toplevel)"/composeApp/build/libs/tether-cli-*.jar \
  "$(git rev-parse --show-toplevel)"/composeApp/build/libs/tether-cli.jar 2>/dev/null | head -1)}"

LOG_C=/tmp/smoke-cliC.log
rm -f /tmp/smoke-cliC-in
mkfifo /tmp/smoke-cliC-in
sleep 600 > /tmp/smoke-cliC-in &
KEEPER_C=$!; disown $KEEPER_C
echo $KEEPER_C > /tmp/smoke-cliC-keeper.pid

nohup java -jar "$JAR" --name SmokeMacA --port 0 < /tmp/smoke-cliC-in > "$LOG_C" 2>&1 &
JPID_C=$!; disown $JPID_C
echo $JPID_C > /tmp/smoke-cliC.pid

for i in $(seq 1 20); do
  echo "list" > /tmp/smoke-cliA-in &
  echo "list" > /tmp/smoke-cliB-in &
  echo "list" > /tmp/smoke-cliC-in &
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
