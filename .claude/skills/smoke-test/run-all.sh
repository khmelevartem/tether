#!/usr/bin/env bash
# Drives the smoke blocks back-to-back in one pass so the run stays inside each CLI's 600s
# keeper window (spreading the blocks out kills CLI A mid-run). Tees a greppable consolidated
# log to stdout and /tmp/smoke-results-<id>.log; report synthesis stays the caller's job.
# Cleanup (block-7) runs unconditionally via the EXIT/INT/TERM trap. A watchdog aborts the
# run after SMOKE_DEADLINE seconds (default 540, under the keeper window) so it can never hang.
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
. ./smoke-env.sh

RESULTS="/tmp/smoke-results-$SMOKE_ID.log"
: > "$RESULTS"
log() { echo "$@" | tee -a "$RESULTS"; }
run() { log "===== $1 ====="; shift; "$@" 2>&1 | tee -a "$RESULTS"; }

# fds detached so the (possibly orphaned) sleep child never holds this script's stdout open.
( sleep "${SMOKE_DEADLINE:-540}"; kill -TERM "$$" ) >/dev/null 2>&1 &
WATCHDOG=$!

cleaned=0
finish() {
  trap - EXIT INT TERM
  if [ "$cleaned" = 0 ]; then
    cleaned=1
    kill "$WATCHDOG" 2>/dev/null; pkill -P "$WATCHDOG" 2>/dev/null
    run BLOCK7 ./block-7-cleanup.sh
    log "ALLDONE"
  fi
  exit 0
}
trap finish EXIT INT TERM

run BLOCK0 ./block-0-preparation.sh
grep -q '^JAR=' "$RESULTS" || { log "SKIP: remaining blocks — cli jar build failed"; exit 1; }

run BLOCK1   ./block-1-desktop-cli-a.sh
run BLOCK2.1 ./block-2.1-single-file-send.sh
run BLOCK2.2 ./block-2.2-multi-file-send.sh
run BLOCK2.3 ./block-2.3-retry.sh
run BLOCK3   ./block-3-same-name-discovery.sh
run BLOCK3.1 ./block-3.1-peer-dedup.sh
run BLOCK3.2 ./block-3.2-same-name-distinct.sh
run BLOCK3.5 ./block-3.5-rename.sh
run BLOCK4-Android ./block-4-android.sh

# block-4-android self-skips without a device; block-5-ios does not, so guard it.
if command -v xcodebuild >/dev/null 2>&1 \
   && xcrun simctl list devices available 2>/dev/null | grep -q "${IOS_DEVICE:-iPhone 17}"; then
  run BLOCK5-iOS ./block-5-ios.sh
else
  run BLOCK5-iOS echo "SKIP: iOS — Xcode or simulator '${IOS_DEVICE:-iPhone 17}' unavailable"
fi

run BLOCK6 ./block-6-graceful-quit.sh
# block-7 runs via the EXIT trap.
