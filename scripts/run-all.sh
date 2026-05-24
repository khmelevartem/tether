#!/bin/bash
# Run all Tether targets in parallel:
#   - Desktop CLI (jar, foreground in current tty via log tail)
#   - Desktop Compose UI
#   - iOS Simulator app
#   - Android emulator app (boots first available AVD if none running)
#
# Logs stream into scripts/.run-all/<target>.log. Press Ctrl-C to stop everything.
#
# Flags:
#   --no-android   skip Android target
#   --no-ios       skip iOS target
#   --no-desktop   skip Desktop UI
#   --no-cli       skip CLI
#   --ios-device <name|udid>   override iOS simulator (default: iPhone 17)

set -u

# Resolve project root from current working directory (not script location), so
# the script operates on the worktree it was invoked from — including when the
# script itself lives in the main checkout and is launched from a worktree.
# Marker = `gradlew` (Gradle wrapper sits at project root).
find_project_root() {
  local dir="$PWD"
  while [ "$dir" != "/" ]; do
    if [ -x "$dir/gradlew" ]; then
      echo "$dir"
      return 0
    fi
    dir="$(dirname "$dir")"
  done
  return 1
}

ROOT="$(find_project_root)" || {
  echo "run-all.sh: no gradlew found walking up from $PWD" >&2
  exit 2
}
cd "$ROOT"
echo "▶ project root: $ROOT"

LOG_DIR="$ROOT/scripts/.run-all"
mkdir -p "$LOG_DIR"

RUN_ANDROID=1
RUN_IOS=1
RUN_DESKTOP=1
RUN_CLI=1
IOS_DEVICE="iPhone 17"

while [ $# -gt 0 ]; do
  case "$1" in
    --no-android) RUN_ANDROID=0 ;;
    --no-ios)     RUN_IOS=0 ;;
    --no-desktop) RUN_DESKTOP=0 ;;
    --no-cli)     RUN_CLI=0 ;;
    --ios-device) IOS_DEVICE="$2"; shift ;;
    -h|--help)
      sed -n '2,18p' "$0"; exit 0 ;;
    *) echo "unknown flag: $1" >&2; exit 2 ;;
  esac
  shift
done

PIDS=()
cleanup() {
  echo
  echo "→ stopping background tasks…"
  for pid in "${PIDS[@]}"; do
    kill "$pid" 2>/dev/null || true
  done
  wait 2>/dev/null || true
  exit 0
}
trap cleanup INT TERM

ANDROID_APP_ID="com.tubetoast.tether"
ANDROID_ACTIVITY="$ANDROID_APP_ID/.MainActivity"
IOS_BUNDLE_ID="com.tubetoast.tether.Tether"
IOS_DERIVED="$ROOT/build/ios"
IOS_APP="$IOS_DERIVED/Build/Products/Debug-iphonesimulator/Tether.app"

start_cli() {
  echo "▶ CLI: installing, then launching tether in a new Terminal window"
  ./gradlew :composeApp:installCli -q
  # `tether` is expected to be on PATH (see README → Desktop CLI install).
  # Open in a separate Terminal.app window so stdin works (CLI is interactive).
  osascript <<'EOF' >/dev/null
tell application "Terminal"
  activate
  do script "exec tether"
end tell
EOF
}

start_desktop_ui() {
  echo "▶ Desktop UI: ./gradlew :composeApp:run"
  ( ./gradlew :composeApp:run -q ) >"$LOG_DIR/desktop-ui.log" 2>&1 &
  PIDS+=($!)
}

start_ios() {
  echo "▶ iOS: $IOS_DEVICE"
  (
    set -e
    # Resolve UDID by name (or accept raw UDID)
    UDID=$(xcrun simctl list devices available \
      | awk -F '[()]' -v n="$IOS_DEVICE" '
          $0 ~ n && $0 !~ /unavailable/ { print $2; exit }
        ')
    if [ -z "$UDID" ]; then
      UDID="$IOS_DEVICE"
    fi
    xcrun simctl boot "$UDID" 2>/dev/null || true
    open -a Simulator
    xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
      -configuration Debug \
      -destination "platform=iOS Simulator,id=$UDID" \
      -derivedDataPath "$IOS_DERIVED" \
      build
    xcrun simctl install "$UDID" "$IOS_APP"
    xcrun simctl launch --console-pty "$UDID" "$IOS_BUNDLE_ID"
  ) >"$LOG_DIR/ios.log" 2>&1 &
  PIDS+=($!)
}

start_android() {
  echo "▶ Android: boot emulator if needed, install & launch"
  (
    set -e
    if ! adb get-state 1>/dev/null 2>&1; then
      EMULATOR_BIN="${ANDROID_HOME:-$HOME/Library/Android/sdk}/emulator/emulator"
      AVD=$("$EMULATOR_BIN" -list-avds | head -n1)
      if [ -z "$AVD" ]; then
        echo "no AVD found; create one in Android Studio" >&2
        exit 1
      fi
      echo "starting emulator: $AVD"
      "$EMULATOR_BIN" -avd "$AVD" -netdelay none -netspeed full &
      # wait for device
      adb wait-for-device
      until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
        sleep 2
      done
    fi
    ./gradlew :composeApp:installDebug -q
    adb logcat -c || true
    adb shell am start -n "$ANDROID_ACTIVITY"
    # Filter to app's own log tags; silence the rest. Adjust tags as needed.
    adb logcat -v time \
      Tether:V TetherApp:V TetherForegroundService:V \
      AndroidRuntime:E System.err:W *:S
  ) >"$LOG_DIR/android.log" 2>&1 &
  PIDS+=($!)
}

[ $RUN_CLI     -eq 1 ] && start_cli
[ $RUN_DESKTOP -eq 1 ] && start_desktop_ui
[ $RUN_IOS     -eq 1 ] && start_ios
[ $RUN_ANDROID -eq 1 ] && start_android

echo
echo "All targets launched. Logs:"
for f in "$LOG_DIR"/*.log; do echo "  tail -f $f"; done
echo
echo "Ctrl-C to stop everything."

# Live-tail aggregated output
tail -F "$LOG_DIR"/*.log &
PIDS+=($!)

wait
