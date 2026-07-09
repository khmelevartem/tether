#!/usr/bin/env bash
set -euo pipefail

# Requires: block-2 (CLI A alive at $LOG_A / fifo $FIFO_A) and block-6.1 (iOS app launched + discovered).
# Sends .txt (stays in Files), .jpg (moved to Photos), and .mp4 (moved to Photos) to the iOS peer.

. "$(dirname "${BASH_SOURCE[0]}")/smoke-env.sh"

cd "$TETHER_ROOT"

if ! command -v xcodebuild >/dev/null 2>&1 \
   || ! xcrun simctl list devices available 2>/dev/null | grep -q "${IOS_DEVICE:-iPhone 17}"; then
  echo "SKIP: iOS receive — Xcode or simulator '${IOS_DEVICE:-iPhone 17}' unavailable"
  exit 0
fi

[ -z "$JAR" ] && { echo "FAIL: cli jar not found — run block-0 first"; exit 1; }
[ -f "$LOG_A" ] || { echo "FAIL: CLI A log not found — run block-2 first"; exit 1; }

IOS_DEVICE="${IOS_DEVICE:-iPhone 17}"
UDID=$(cat "$IOS_UDID_FILE" 2>/dev/null || \
  xcrun simctl list devices available \
    | awk -F '[()]' -v n="$IOS_DEVICE" '$0 ~ n && $0 !~ /unavailable/ { print $2; exit }')
[ -z "$UDID" ] && { echo "FAIL: could not resolve simulator UDID"; exit 1; }

IOS_BUNDLE_ID=com.tubetoast.tether.Tether

# Pre-grant add-only Photos so the OS save isn't prompt-blocked.
set +e; xcrun simctl privacy "$UDID" grant photos-add "$IOS_BUNDLE_ID" 2>/dev/null; GRANT_RC=$?; set -e
[ $GRANT_RC -eq 0 ] \
  && echo "PASS: photos-add pre-granted" \
  || echo "SKIP: photos-add grant returned non-zero (will proceed; Photos framework may still work)"

# Resolve the iOS peer name — block-6.1 writes it to $SMOKE_DIR/ios-name.txt after dns-sd discovery.
IOS_NAME=$(cat "$SMOKE_DIR/ios-name.txt" 2>/dev/null | tr -d '\n' || true)
[ -n "$IOS_NAME" ] || { echo "FAIL: iOS peer name not found ($SMOKE_DIR/ios-name.txt missing) — run block-6.1 first"; exit 1; }
echo "iOS peer: $IOS_NAME"

# block-6.1's Keychain test leaves the app in a state where CLI A tracks it via the IPv6 loopback
# address (0:0:0:0:0:0:0:1), which Ktor rejects as an unparseable URL. Terminate and cold-start
# the app so mDNS runs a clean browse cycle that resolves to a routable address.
xcrun simctl terminate "$UDID" "$IOS_BUNDLE_ID" 2>/dev/null || true
sleep 2
xcrun simctl launch "$UDID" "$IOS_BUNDLE_ID" >/dev/null 2>&1
# Wait until CLI A's peer list shows the iOS peer with an IPv4 address (mDNS re-browse).
IPV4_RE="${IOS_NAME}@[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+"
IPV4_SEEN=0
PEERS_BEFORE=$(grep -c '^\[peers\]' "$LOG_A" 2>/dev/null || true)
PEERS_BEFORE="${PEERS_BEFORE:-0}"
for i in $(seq 1 45); do
  set +e
  # Only consider [peers] lines that appeared after the re-launch.
  PEERS_NOW=$(grep -c '^\[peers\]' "$LOG_A" 2>/dev/null || true)
  PEERS_NOW="${PEERS_NOW:-0}"
  if [ "$PEERS_NOW" -gt "$PEERS_BEFORE" ]; then
    LAST_PEERS=$(grep '^\[peers\]' "$LOG_A" 2>/dev/null | tail -1)
    echo "$LAST_PEERS" | grep -qE "$IPV4_RE"
    PEER_RC=$?
  else
    PEER_RC=1
  fi
  set -e
  [ $PEER_RC -eq 0 ] && IPV4_SEEN=1 && break
  sleep 1
done
set +e; LAST_PEERS=$(grep '^\[peers\]' "$LOG_A" 2>/dev/null | tail -1); set -e
[ "$IPV4_SEEN" -eq 1 ] \
  || { echo "FAIL: iOS peer did not appear with IPv4 address after re-launch — last peers: $LAST_PEERS"; exit 1; }

# Build send files under $SMOKE_DIR with $SMOKE_SEND_PREFIX.
TS=$(date +%s)
TXT_NAME="${SMOKE_SEND_PREFIX}-ios-recv-${TS}.txt"
JPG_NAME="${SMOKE_SEND_PREFIX}-ios-recv-${TS}.jpg"
MP4_NAME="${SMOKE_SEND_PREFIX}-ios-recv-${TS}.mp4"
TXT_SRC="$SMOKE_DIR/$TXT_NAME"
JPG_SRC="$SMOKE_DIR/$JPG_NAME"
MP4_SRC="$SMOKE_DIR/$MP4_NAME"

echo "smoke-ios-receive-$TS" > "$TXT_SRC"

# Generate a real 1×1 JPEG via Python + sips so UTType classifies it as public.image.
PNG_TMP="$SMOKE_DIR/1x1-$TS.png"
python3 - "$PNG_TMP" <<'PYEOF'
import sys, struct, zlib
def chunk(tag, data):
    c = tag + data
    return struct.pack('>I', len(data)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)
sig = b'\x89PNG\r\n\x1a\n'
ihdr = chunk(b'IHDR', struct.pack('>IIBBBBB', 1, 1, 8, 2, 0, 0, 0))
idat = chunk(b'IDAT', zlib.compress(b'\x00\xff\xff\xff'))
iend = chunk(b'IEND', b'')
open(sys.argv[1], 'wb').write(sig + ihdr + idat + iend)
PYEOF
sips -s format jpeg "$PNG_TMP" --out "$JPG_SRC" >/dev/null 2>&1
rm -f "$PNG_TMP"

# Generate a short H.264 test video via ffmpeg (public.movie → Photos).
MP4_AVAILABLE=0
if command -v ffmpeg >/dev/null 2>&1; then
  ffmpeg -f lavfi -i testsrc=duration=1:size=320x240:rate=15 -pix_fmt yuv420p -c:v libx264 \
    -y "$MP4_SRC" >/dev/null 2>&1 \
    && MP4_AVAILABLE=1 \
    || echo "NOTE: ffmpeg present but video generation failed — video assertion skipped"
else
  echo "NOTE: ffmpeg not found — video assertion skipped"
fi

echo "Send files prepared: $TXT_NAME, $JPG_NAME$([ $MP4_AVAILABLE -eq 1 ] && echo ", $MP4_NAME" || true)"

# Snapshot the iOS log before sending so we can isolate new lines.
IOS_LOG_BEFORE="$SMOKE_DIR/ios-log-before-recv.txt"
xcrun simctl spawn "$UDID" log show \
  --predicate 'process == "Tether"' \
  --style syslog --last 5m 2>/dev/null > "$IOS_LOG_BEFORE" || true

# Send .txt to iOS peer; wait for [send] done.
DONE_BEFORE_TXT=$(grep -c '^\[send\] done' "$LOG_A" 2>/dev/null || true)
DONE_BEFORE_TXT="${DONE_BEFORE_TXT:-0}"
echo "send \"$IOS_NAME\" $TXT_SRC" > "$FIFO_A" &
TXT_DONE=0
for i in $(seq 1 30); do
  set +e; DONE_NOW=$(grep -c '^\[send\] done' "$LOG_A" 2>/dev/null || true); set -e
  DONE_NOW="${DONE_NOW:-0}"
  [ "$DONE_NOW" -gt "$DONE_BEFORE_TXT" ] && TXT_DONE=1 && break
  sleep 1
done

# Send .jpg to iOS peer; wait for [send] done.
DONE_BEFORE_JPG=$(grep -c '^\[send\] done' "$LOG_A" 2>/dev/null || true)
DONE_BEFORE_JPG="${DONE_BEFORE_JPG:-0}"
echo "send \"$IOS_NAME\" $JPG_SRC" > "$FIFO_A" &
JPG_DONE=0
for i in $(seq 1 30); do
  set +e; DONE_NOW=$(grep -c '^\[send\] done' "$LOG_A" 2>/dev/null || true); set -e
  DONE_NOW="${DONE_NOW:-0}"
  [ "$DONE_NOW" -gt "$DONE_BEFORE_JPG" ] && JPG_DONE=1 && break
  sleep 1
done

# Send .mp4 to iOS peer (if generated); wait for [send] done.
MP4_DONE=0
if [ "$MP4_AVAILABLE" -eq 1 ]; then
  DONE_BEFORE_MP4=$(grep -c '^\[send\] done' "$LOG_A" 2>/dev/null || true)
  DONE_BEFORE_MP4="${DONE_BEFORE_MP4:-0}"
  echo "send \"$IOS_NAME\" $MP4_SRC" > "$FIFO_A" &
  for i in $(seq 1 30); do
    set +e; DONE_NOW=$(grep -c '^\[send\] done' "$LOG_A" 2>/dev/null || true); set -e
    DONE_NOW="${DONE_NOW:-0}"
    [ "$DONE_NOW" -gt "$DONE_BEFORE_MP4" ] && MP4_DONE=1 && break
    sleep 1
  done
fi

# Give iOS time to write to Files and move media to Photos.
sleep 5

# Locate app data container.
CONTAINER=$(xcrun simctl get_app_container "$UDID" "$IOS_BUNDLE_ID" data 2>/dev/null || true)
# Non-media files land directly in Documents/ (not in a Tether subdirectory).
DOCS_DIR="$CONTAINER/Documents"

# Assert .txt: must be present in Documents/ and byte-identical.
if [ "$TXT_DONE" -eq 0 ]; then
  echo "FAIL: iOS receive .txt — [send] done not seen in CLI A log"
elif [ -z "$CONTAINER" ]; then
  echo "FAIL: iOS receive .txt — could not resolve app container"
elif [ ! -f "$DOCS_DIR/$TXT_NAME" ]; then
  echo "FAIL: iOS receive .txt — $TXT_NAME not found in Documents/"
elif ! diff "$TXT_SRC" "$DOCS_DIR/$TXT_NAME" >/dev/null 2>&1; then
  echo "FAIL: iOS receive .txt — content differs from source"
else
  echo "PASS: iOS receive .txt — byte-identical in Documents/"
fi

# Capture new iOS log lines (those not present before the send).
IOS_LOG_AFTER="$SMOKE_DIR/ios-log-after-recv.txt"
xcrun simctl spawn "$UDID" log show \
  --predicate 'process == "Tether"' \
  --style syslog --last 5m 2>/dev/null > "$IOS_LOG_AFTER" || true
NEW_LOG=$(comm -13 <(sort "$IOS_LOG_BEFORE") <(sort "$IOS_LOG_AFTER") 2>/dev/null \
  || cat "$IOS_LOG_AFTER")

# Assert .jpg: must be ABSENT from Documents/ (moved to Photos) + Photos-save log if visible.
if [ "$JPG_DONE" -eq 0 ]; then
  echo "FAIL: iOS receive .jpg — [send] done not seen in CLI A log"
elif [ -z "$CONTAINER" ]; then
  echo "FAIL: iOS receive .jpg — could not resolve app container"
elif [ -f "$DOCS_DIR/$JPG_NAME" ]; then
  echo "FAIL: iOS receive .jpg — file still present in Documents/ after settle (expected move to Photos)"
else
  set +e
  PHOTOS_LOG=$(echo "$NEW_LOG" | grep -i "PhotosSave" | grep -i "saved" | grep "$JPG_NAME" | head -1 || true)
  set -e
  if [ -n "$PHOTOS_LOG" ]; then
    echo "PASS: iOS receive .jpg — absent from Documents/ + Tether.PhotosSave log confirmed"
    echo "  log: $PHOTOS_LOG"
  else
    echo "PASS: iOS receive .jpg — absent from Documents/ (move to Photos inferred by absence; Tether.PhotosSave not surfaced via simctl)"
  fi
fi

# Assert .mp4: must be ABSENT from Documents/ (moved to Photos); skip if ffmpeg was unavailable.
if [ "$MP4_AVAILABLE" -eq 0 ]; then
  echo "SKIP: iOS receive .mp4 — ffmpeg not available, video not generated"
elif [ "$MP4_DONE" -eq 0 ]; then
  echo "FAIL: iOS receive .mp4 — [send] done not seen in CLI A log"
elif [ -z "$CONTAINER" ]; then
  echo "FAIL: iOS receive .mp4 — could not resolve app container"
elif [ -f "$DOCS_DIR/$MP4_NAME" ]; then
  echo "FAIL: iOS receive .mp4 — file still present in Documents/ after settle (expected move to Photos)"
else
  set +e
  PHOTOS_LOG_MP4=$(echo "$NEW_LOG" | grep -i "PhotosSave" | grep -i "saved" | grep "$MP4_NAME" | head -1 || true)
  set -e
  if [ -n "$PHOTOS_LOG_MP4" ]; then
    echo "PASS: iOS receive .mp4 — absent from Documents/ + Tether.PhotosSave log confirmed"
    echo "  log: $PHOTOS_LOG_MP4"
  else
    echo "PASS: iOS receive .mp4 — absent from Documents/ (move to Photos inferred by absence; Tether.PhotosSave not surfaced via simctl)"
  fi
fi
