#!/usr/bin/env bash
set -euo pipefail

# Requires: block-1 already executed (CLI A alive, LOG_A set or /tmp/smoke-cliA.log present).

LOG_A="${LOG_A:-/tmp/smoke-cliA.log}"

# Pre-checks
if ! xcrun simctl help >/dev/null 2>&1; then
  echo "SKIP: Xcode CLI tools not installed"
  exit 0
fi
if [ ! -d "$(git rev-parse --show-toplevel)/iosApp/iosApp.xcodeproj" ]; then
  echo "SKIP: iosApp/iosApp.xcodeproj not found"
  exit 0
fi

cd "$(git rev-parse --show-toplevel)"

IOS_DEVICE="${IOS_DEVICE:-iPhone 17}"
UDID=$(xcrun simctl list devices available \
  | awk -F '[()]' -v n="$IOS_DEVICE" '$0 ~ n && $0 !~ /unavailable/ { print $2; exit }')
if [ -z "$UDID" ]; then
  echo "SKIP: no available simulator matching '$IOS_DEVICE'"
  exit 0
fi

set +e; xcrun simctl boot "$UDID" 2>/dev/null; set -e
open -a Simulator

IOS_DERIVED=build/ios
IOS_APP="$IOS_DERIVED/Build/Products/Debug-iphonesimulator/Tether.app"
IOS_BUNDLE_ID=com.tubetoast.tether.Tether

xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug \
  -destination "platform=iOS Simulator,id=$UDID" \
  -derivedDataPath "$IOS_DERIVED" \
  build > /tmp/smoke-ios-build.log 2>&1
echo "PASS: xcodebuild"

xcrun simctl install "$UDID" "$IOS_APP"
echo "PASS: install"

xcrun simctl launch "$UDID" "$IOS_BUNDLE_ID" > /tmp/smoke-ios-launch.log 2>&1
echo "PASS: launch"

# mDNS publish
IOS_NAME=""
for i in $(seq 1 30); do
  set +e
  IOS_NAME=$( ( dns-sd -B _tether._tcp local. & DNSSD=$!; sleep 2; kill $DNSSD 2>/dev/null ) \
    | awk -F'\t' '/_tether._tcp/ && NF>1 { print $NF }' \
    | grep -E 'iPhone|iPad' | head -1 | tr -d '\r' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
  set -e
  [ -n "$IOS_NAME" ] && break
  sleep 1
done
[ -n "$IOS_NAME" ] && echo "PASS: mDNS publish — $IOS_NAME" || echo "FAIL: mDNS publish — no iOS service seen (check Local Network Privacy prompt)"

# TXT record
set +e
TXT_OK=$( ( dns-sd -q "${IOS_NAME}._tether._tcp.local." TXT & DNSSD=$!; sleep 3; kill $DNSSD 2>/dev/null ) 2>&1 | grep -c '03 76 3D 31' || echo 0)
set -e
[ "$TXT_OK" -gt 0 ] && echo "PASS: TXT publish — v=1 record present" || echo "FAIL: TXT publish — 03 76 3D 31 not seen"

# Cross-discovery
for i in $(seq 1 30); do
  set +e; grep -q "$IOS_NAME" "$LOG_A" 2>/dev/null; RC=$?; set -e
  [ $RC -eq 0 ] && break
  sleep 1
done
set +e; grep -q "$IOS_NAME" "$LOG_A" 2>/dev/null; RC=$?; set -e
[ $RC -eq 0 ] && echo "PASS: cross-discovery — iOS peer seen on Desktop A" || echo "FAIL: cross-discovery — $IOS_NAME not seen in Desktop A log"

export UDID
