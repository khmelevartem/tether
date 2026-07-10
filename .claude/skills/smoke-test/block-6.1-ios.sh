#!/usr/bin/env bash
set -euo pipefail

# Requires: block-2 already executed (CLI A alive, log at $LOG_A).

. "$(dirname "${BASH_SOURCE[0]}")/smoke-env.sh"

cd "$TETHER_ROOT"

IOS_DEVICE="${IOS_DEVICE:-iPhone 17}"
UDID=$(xcrun simctl list devices available \
  | awk -F '[()]' -v n="$IOS_DEVICE" '$0 ~ n && $0 !~ /unavailable/ { print $2; exit }')
echo "$UDID" > "$IOS_UDID_FILE"

set +e; xcrun simctl boot "$UDID" 2>/dev/null; set -e
open -a Simulator

IOS_DERIVED=build/ios
IOS_APP="$IOS_DERIVED/Build/Products/Debug-iphonesimulator/Tether.app"
IOS_BUNDLE_ID=com.tubetoast.tether.Tether

xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug \
  -destination "platform=iOS Simulator,id=$UDID" \
  -derivedDataPath "$IOS_DERIVED" \
  build > "$IOS_BUILD_LOG" 2>&1
echo "PASS: xcodebuild"

xcrun simctl install "$UDID" "$IOS_APP"
echo "PASS: install"

xcrun simctl launch "$UDID" "$IOS_BUNDLE_ID" > "$IOS_LAUNCH_LOG" 2>&1
echo "PASS: launch"

# mDNS publish
IOS_NAME=""
for i in $(seq 1 30); do
  set +e
  IOS_NAME=$( ( dns-sd -B _tether._tcp . & DNSSD=$!; sleep 2; kill $DNSSD 2>/dev/null ) 2>&1 \
    | grep '_tether._tcp' \
    | sed 's/.*_tether\._tcp\.[[:space:]]*//' \
    | grep -E 'iPhone|iPad' | head -1 | tr -d '\r' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
  set -e
  [ -n "$IOS_NAME" ] && break
  sleep 1
done
[ -n "$IOS_NAME" ] && echo "PASS: mDNS publish — $IOS_NAME" || echo "FAIL: mDNS publish — no iOS service seen (check Local Network Privacy prompt)"
echo "$IOS_NAME" > "$SMOKE_DIR/ios-name.txt"

# TXT record — must carry an fp= field (66 70 3D = "fp="). Length-agnostic: the
# fingerprint is the hex SHA-256 of the public key, so the record length depends on it.
set +e
TXT_LINE=$( ( dns-sd -q "${IOS_NAME}._tether._tcp.local." TXT & DNSSD=$!; sleep 3; kill $DNSSD 2>/dev/null ) 2>&1 | grep -E 'TXT.*IN.*[0-9]+ bytes.* 66 70 3D' | head -1)
set -e
[ -n "$TXT_LINE" ] && echo "PASS: TXT publish — #fp fingerprint record present" || echo "FAIL: TXT publish — #fp record not seen"

# Cross-discovery
for i in $(seq 1 30); do
  set +e; grep -q "$IOS_NAME" "$LOG_A" 2>/dev/null; RC=$?; set -e
  [ $RC -eq 0 ] && break
  sleep 1
done
set +e; grep -q "$IOS_NAME" "$LOG_A" 2>/dev/null; RC=$?; set -e
[ $RC -eq 0 ] && echo "PASS: cross-discovery — iOS peer seen on Desktop A" || echo "FAIL: cross-discovery — $IOS_NAME not seen in Desktop A log"

# /health on real iOS bundle (port via lsof on host loopback; simulator binds to host's localhost)
IOS_PORT=$(lsof -nP -iTCP -a -c Tether 2>/dev/null \
  | awk '/LISTEN/ && $1=="Tether" {print $9}' | sed 's/.*://' | head -1)
if [ -z "$IOS_PORT" ]; then
  echo "FAIL: /health — iOS FileServer port not found via lsof"
else
  set +e; curl -sf --max-time 5 "http://localhost:$IOS_PORT/health" | grep -q "Tether OK"; RC=$?; set -e
  [ $RC -eq 0 ] && echo "PASS: /health — Tether OK on $IOS_PORT" || echo "FAIL: /health — bad response on $IOS_PORT"
fi

# /pair X.509 EC P-256 SPKI shape — load-bearing gate for real-Keychain regression
PAIR_RESP=""
if [ -n "$IOS_PORT" ]; then
  PAIR_RESP=$(curl -sf --max-time 5 -X POST "http://localhost:$IOS_PORT/pair" \
    -H "Content-Type: application/json" \
    -d '{"publicKey":[1,2,3], "deviceName":"smoke"}')
  set +e
  echo "$PAIR_RESP" | jq -e '.publicKey | length == 91 and .[0] == 48 and .[26] == 4' > /dev/null
  RC=$?
  set -e
  [ $RC -eq 0 ] && echo "PASS: /pair X.509 EC P-256 SPKI (91 bytes, real Keychain)" \
    || echo "FAIL: /pair bad shape — $PAIR_RESP"
fi

# Keychain persistence across cold launches
if [ -n "$PAIR_RESP" ]; then
  K1=$(echo "$PAIR_RESP" | jq -c '.publicKey')
  xcrun simctl terminate "$UDID" "$IOS_BUNDLE_ID" 2>/dev/null
  sleep 2
  xcrun simctl launch "$UDID" "$IOS_BUNDLE_ID" >/dev/null
  sleep 5
  IOS_PORT2=$(lsof -nP -iTCP -a -c Tether 2>/dev/null \
    | awk '/LISTEN/ && $1=="Tether" {print $9}' | sed 's/.*://' | head -1)
  K2=$(curl -sf --max-time 5 -X POST "http://localhost:$IOS_PORT2/pair" \
    -H "Content-Type: application/json" \
    -d '{"publicKey":[1,2,3], "deviceName":"smoke"}' | jq -c '.publicKey')
  [ -n "$K1" ] && [ "$K1" = "$K2" ] \
    && echo "PASS: publicKey identical across cold launches (Keychain persisted)" \
    || echo "FAIL: publicKey changed across cold launches"
fi

