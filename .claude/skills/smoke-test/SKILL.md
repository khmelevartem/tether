---
name: smoke-test
description: Run a basic smoke test (happy-path) across Tether platforms — Desktop CLI (cli jar + /health + mDNS + stdin commands), Desktop↔Desktop send via CLI, Android (if an adb device is connected) with send-from-Desktop, iOS simulator (xcodebuild + simctl) with mDNS publish + cross-discovery. Use this skill when the user says "run smoke", "run smoke test", "basic smoke", "basic regression", "check the build across platforms before merge", "smoke test". Not to be confused with unit tests (`./gradlew allTests`) — smoke is a runtime check that everything starts and peers can discover each other, not logic correctness.
---

# Smoke-test skill

Runs basic smoke scenarios across Tether targets and produces a human-readable report.

**This is smoke, not regression.** The goal — in 1–3 minutes — is to see that nothing is fundamentally broken (CLI starts, FileServer responds, mDNS is published, stdin commands work, send roundtrip OK, native targets compile). Business logic correctness is the job of `./gradlew allTests`.

## What the skill does NOT check (out of scope for automation)

At the start of the run **tell the user** — coverage boundaries:

- **Physical iPhone** — no, requires manual signing and certificate trust.
- **macOS** — ships through the Desktop JVM target; smoke is covered by the Desktop block (the same jar is packaged into `.app`/`.dmg` via `packageReleaseDistributionForCurrentOS`).
- **iOS Local Network Privacy prompt** — on the first app launch on the simulator iOS may show "Allow Local Network access". Without Allow, `NSNetService.publish()` silently fails. If the iOS block fails on publish — check the prompt manually, grant Allow, restart the smoke.
- **Android-initiated send (Android → Desktop)** — Android has no programmatic send trigger (intent / UI button / broadcast). The skill checks the reverse direction: Desktop → Android via CLI `send`.
- **Tapping the Notification "Stop" button** — replaced by `am force-stop` or broadcast. That the *button is rendered and works* — verify manually.
- **Sleep/wake of a real device** — `adb shell input keyevent SLEEP/WAKEUP` is an approximation, not a real power state.
- **Rotation effects on FGS survivability** — emulator ≠ real device.

All these items must appear in the **"Manual verification required"** section of the report.

## Starting the CLI

```bash
./gradlew :composeApp:cliJar -q
JAR=$(ls composeApp/build/libs/tether-cli*.jar 2>/dev/null | head -1)
[ -z "$JAR" ] && { echo "cli jar not found"; exit 1; }
java -jar "$JAR" --name SmokeMacA --port 0 < fifo
```

The FIFO keeps stdin open for `list`, `send`, `quit` commands.

## Run plan

The skill executes blocks sequentially. A block failure does not prevent subsequent blocks from running. Cleanup is performed **always**, even if there were earlier FAILs.

### Block 0: Preparation

1. Make sure no CLI instances are running:
   ```bash
   pgrep -fl 'com.tubetoast.tether-.*\.jar|composeApp:run' || echo "clean"
   ```
   If there are — `kill` them: external mDNS services interfere with the run.
2. Build the CLI jar:
   ```bash
   ./gradlew :composeApp:cliJar -q
   JAR=$(ls composeApp/build/libs/tether-cli-*.jar composeApp/build/libs/tether-cli.jar 2>/dev/null | head -1)
   ```
   Remember the path.

If the build fails or the JAR is not found — all remaining blocks SKIP with reason "cli jar build failed".

### Block 1: Desktop CLI (instance A)

Launch via FIFO (stdin keeper). Name — `SmokeMacA`, must match what Block 2 looks for in instance B's log.

```bash
LOG_A=/tmp/smoke-cliA.log
mkfifo /tmp/smoke-cliA-in
sleep 600 > /tmp/smoke-cliA-in &
KEEPER_A=$!; disown $KEEPER_A
echo $KEEPER_A > /tmp/smoke-cliA-keeper.pid

nohup java -jar "$JAR" --name SmokeMacA --port 0 < /tmp/smoke-cliA-in > "$LOG_A" 2>&1 &
JPID_A=$!; disown $JPID_A
echo $JPID_A > /tmp/smoke-cliA.pid
```

Wait up to 30 sec, polling the log:
```bash
for i in $(seq 1 30); do grep -q 'FileServer started' $LOG_A && break; sleep 1; done
PORT_A=$(grep -oE 'port[[:space:]]*:[[:space:]]*[0-9]+' $LOG_A | grep -oE '[0-9]+' | head -1)
```

Scenarios:
1. **Startup** — port parsed, java pid is alive (`ps -p $JPID_A`). PASS if both conditions met.
2. **`/health`** — `curl -sf --max-time 5 http://localhost:$PORT_A/health` → must return `Tether OK`.
3. **`/pair` — public key format.** The endpoint returns an X.509-encoded EC P-256 SubjectPublicKeyInfo: exactly 91 bytes, first byte `0x30` (DER `SEQUENCE`), byte 26 — `0x04` (uncompressed EC point marker). We check the shape, not "non-empty response" — a placeholder would pass a superficial check.
   ```bash
   PAIR_RESP=$(curl -sf --max-time 5 -X POST http://localhost:$PORT_A/pair \
     -H "Content-Type: application/json" \
     -d '{"publicKey":[1,2,3], "deviceName":"smoke"}')
   echo "$PAIR_RESP" | jq -e '.publicKey | length == 91 and .[0] == 48 and .[26] == 4' > /dev/null \
     && echo "PASS: X.509 EC P-256 SubjectPublicKeyInfo" \
     || { echo "FAIL: bad publicKey shape: $PAIR_RESP"; }
   ```
4. **Port LISTEN** — `lsof -nP -iTCP:$PORT_A | head -3` shows a java listener.
5. **mDNS publish (primary)** — poll the CLI log, look for `mDNS started → advertising 'SmokeMacA' on port`.
6. **mDNS publish (secondary, optional)** — `( dns-sd -B _tether._tcp. local. 2>&1 & DNSSD_PID=$!; sleep 8; kill $DNSSD_PID 2>/dev/null ) | grep SmokeMacA`. If `dns-sd` is unavailable (Linux) — this step SKIP, the overall result is still PASS via primary.
7. **stdin `list`** — `echo "list" > /tmp/smoke-cliA-in &; sleep 1; tail $LOG_A` — must print a `[list]` or `[peers]` line.

Keep instance A alive until the end of Block 3. Graceful `quit` check — in Block 4.

### Block 2: Desktop ↔ Desktop send (via CLI)

**Important:** send must go via the **CLI `send` command**, not via `curl POST /upload`. This is a smoke test of the user scenario, not of the endpoint.

Start a second CLI instance (`SmokeMacB`) in parallel with A, wait until mDNS lets both see each other, send `send SmokeMacB <path>` to stdin A.

```bash
LOG_B=/tmp/smoke-cliB.log
mkfifo /tmp/smoke-cliB-in
sleep 600 > /tmp/smoke-cliB-in & KEEPER_B=$!; disown $KEEPER_B
echo $KEEPER_B > /tmp/smoke-cliB-keeper.pid
nohup java -jar "$JAR" --name SmokeMacB --port 0 < /tmp/smoke-cliB-in > "$LOG_B" 2>&1 &
JPID_B=$!; disown $JPID_B
echo $JPID_B > /tmp/smoke-cliB.pid

# Names must exactly match the --name above.
for i in $(seq 1 30); do
  grep -q 'SmokeMacA' $LOG_B 2>/dev/null && \
  grep -q 'SmokeMacB' $LOG_A 2>/dev/null && break
  sleep 1
done

echo "send-via-cli-$(date +%s)" > /tmp/smoke-send.txt
echo "send SmokeMacB /tmp/smoke-send.txt" > /tmp/smoke-cliA-in &

for i in $(seq 1 15); do
  grep -qE "^\[send\] (OK|FAIL)" $LOG_A && break
  sleep 1
done
SEND_LINE=$(grep -E "^\[send\] (OK|FAIL)" $LOG_A | tail -1)
echo "$SEND_LINE"

# Parse savedPath from the line "[send] OK — <ms> ms → <savedPath>", don't guess the directory.
SAVED_B=$(echo "$SEND_LINE" | sed -nE 's/.*→[[:space:]]+(.+)$/\1/p')
if [ -n "$SAVED_B" ] && [ -f "$SAVED_B" ]; then
  diff /tmp/smoke-send.txt "$SAVED_B" && echo PASS || echo FAIL
else
  echo "FAIL: savedPath not parsed or file missing"
fi
```

PASS if:
1. log A contains `[send] OK — <ms> ms → <savedPath>`
2. the file at `savedPath` is identical to the original

Cleanup of instance B — in Block 7.

### Block 3: Same-name discovery

Verifies that three peers with the same requested service name see each other after mDNS conflict-rename: a third instance is launched with the same `--name SmokeMacA` as A, in parallel with A and B.

```bash
LOG_C=/tmp/smoke-cliC.log
mkfifo /tmp/smoke-cliC-in
sleep 600 > /tmp/smoke-cliC-in & KEEPER_C=$!; disown $KEEPER_C
echo $KEEPER_C > /tmp/smoke-cliC-keeper.pid
nohup java -jar "$JAR" --name SmokeMacA --port 0 < /tmp/smoke-cliC-in > "$LOG_C" 2>&1 &
JPID_C=$!; disown $JPID_C
echo $JPID_C > /tmp/smoke-cliC.pid

for i in $(seq 1 20); do
  echo "list" > /tmp/smoke-cliA-in &
  echo "list" > /tmp/smoke-cliB-in &
  echo "list" > /tmp/smoke-cliC-in &
  sleep 1
  # The `[peers]` line starts with an ANSI escape (`\x1b[1A\r\x1b[K`); catch name up to `@`,
  # to capture the renamed form `SmokeMacA (2)`.
  A_OK=$(grep -aE "\[peers\]" $LOG_A | tail -1 | grep -oE 'SmokeMac[A-Z][^@]*' | sort -u | wc -l | tr -d ' ')
  B_OK=$(grep -aE "\[peers\]" $LOG_B | tail -1 | grep -oE 'SmokeMac[A-Z][^@]*' | sort -u | wc -l | tr -d ' ')
  C_OK=$(grep -aE "\[peers\]" $LOG_C | tail -1 | grep -oE 'SmokeMac[A-Z][^@]*' | sort -u | wc -l | tr -d ' ')
  [ "$A_OK" -ge 2 ] && [ "$B_OK" -ge 2 ] && [ "$C_OK" -ge 2 ] && break
done
```

PASS if each of A/B/C sees ≥ 2 unique SmokeMac peers within 20 sec. FAIL — attach the last `[peers]` lines from all three logs in Details.

Cleanup of instance C — in Block 7.

### Block 3.5: Device name rename — peer sees the new name

stdin `name <new>` on A; B must see the new name via mDNS republish.

```bash
echo "name RenamedA" > /tmp/smoke-cliA-in &
for i in $(seq 1 15); do
  grep -q "RenamedA" $LOG_B && break
  sleep 1
done
grep -q "RenamedA" $LOG_B && echo PASS || echo FAIL
```

### Block 4: Graceful quit of instance A

`echo "quit" > /tmp/smoke-cliA-in &`, wait up to 8 sec, check `ps -p $JPID_A`. PASS if the process exited. If not — FAIL "not graceful", `kill -9` and move on.

### Block 5: Android (conditional)

First check for a device:
```bash
adb devices | awk '/device$/ && !/List/ {print $1}'
```

If empty — the entire block SKIP with reason "no adb device connected".

If a device is present:

1. **Install:** `./gradlew -q :composeApp:installDebug`
2. **Logcat clear:** `adb logcat -c`
3. **Start activity:**
   ```bash
   adb shell am start -n com.tubetoast.tether/.MainActivity
   ```
4. **Wait for `NSD service registered` as the readiness anchor.**
   The anchor for the cross-discovery metric is placed **after** the Android side has published — the delta measures only network propagation + JmDNS resolve, without Android boot/init:
   ```bash
   DEADLINE=$(($(date +%s) + 12))
   while [ $(date +%s) -lt $DEADLINE ]; do
     adb logcat -d 2>/dev/null | grep -q "NSD service registered" && break
     sleep 1
   done
   NSD_READY_MS=$(python3 -c "import time; print(int(time.time() * 1000))")
   ```
   Parse:
   - `TetherFGService: FileServer started on port <N>` → `ANDROID_PORT`
   - `Starting NSD: name=Tether-...` and `NSD service registered: ...` — time difference = NSD probing latency, output in Details.
5. **Get IP** via `ip addr`, not `ip route` — the format differs on some vendors (ColorOS, MIUI):
   ```bash
   ANDROID_IP=$(adb shell ip addr show wlan0 2>&1 | grep "inet " | awk '{print $2}' | cut -d/ -f1 | head -1)
   ```
   If emulator and IP is `10.0.2.x` — host access via `adb forward tcp:18080 tcp:$ANDROID_PORT` and `localhost:18080`. For a physical device — directly `$ANDROID_IP:$ANDROID_PORT`.
6. **`/health` sanity:** `curl -sf http://$ANDROID_IP:$ANDROID_PORT/health` → `Tether OK`. This is the only place where curl is acceptable — endpoint sanity, not a user flow.
7. **Cross-discovery with timing:** parse the Desktop CLI's `[peers]` line by the Android device's IP — Android advertises under the device name (`CPH2653`, `Pixel 7`, vendor-specific), not under any `Tether-*` prefix. Match the entry that ends with `@$ANDROID_IP:port` and strip everything after `@`:
   ```bash
   for i in $(seq 1 30); do
     sleep 1
     grep -aE "\[peers\] .*@${ANDROID_IP}:" $LOG_A | tail -1 | grep -q . && break
   done
   NOW_MS=$(python3 -c "import time; print(int(time.time() * 1000))")
   DELTA_MS=$((NOW_MS - NSD_READY_MS))
   ANDROID_NAME=$(grep -aE "\[peers\]" $LOG_A | tail -1 | grep -oE '[^, ]+@'"$ANDROID_IP" | head -1 | sed 's/@.*//')
   echo "cross-discovery: ${DELTA_MS}ms, peer=$ANDROID_NAME"
   ```
   In the report: `Android | cross-discovery | ✓ PASS | 250 ms` — network-propagation + JmDNS resolve.
8. **Send Desktop → Android (via CLI):**
   ```bash
   if echo "$ANDROID_IP" | grep -q "^10\.0\.2\."; then
     echo "SKIP: Android emulator detected — QEMU user-mode NAT drops host→guest TCP payload; see docs/knowledge/android-emulator-networking.md"
   elif [ -z "$ANDROID_NAME" ]; then
     echo "SKIP: cross-discovery did not surface Android peer"
   else
     echo "send-to-android-$(date +%s)" > /tmp/smoke-android.txt
     echo "send $ANDROID_NAME /tmp/smoke-android.txt" > /tmp/smoke-cliA-in &
     for i in $(seq 1 15); do
       grep -qE "^\[send\] (OK|FAIL)" $LOG_A && break
       sleep 1
     done
     SEND_LINE=$(grep -E "^\[send\] (OK|FAIL)" $LOG_A | tail -1)
     # On Android savedPath is absolute — cat it directly via adb shell
     SAVED_PATH=$(echo "$SEND_LINE" | sed -nE 's/.*→[[:space:]]+(.+)$/\1/p')
     adb shell cat "$SAVED_PATH" 2>/dev/null | diff - /tmp/smoke-android.txt && echo PASS || echo FAIL
   fi
   ```
   PASS if Desktop CLI log has `[send] OK` AND the file on Android at the parsed savedPath is identical. SKIP if `10.0.2.x` (QEMU NAT) or ANDROID_NAME is empty — not FAIL.
9. **Stop service:** `adb shell am force-stop com.tubetoast.tether`. PASS if the app exited. (Notification "Stop" tap — manual.)

Mark each sub-scenario separately: install, FGS+mDNS up (with NSD probing latency), /health sanity, cross-discovery (with ms), send-desktop-to-android, stop.

### Block 5.5: iOS simulator runtime (conditional)

Pre-checks (any fail → entire block SKIP with reason):
- `xcrun simctl help >/dev/null 2>&1` — Xcode CLI tools installed.
- `[ -d iosApp/iosApp.xcodeproj ]` — project exists.

Otherwise:

1. **Resolve + boot the simulator.** Default `iPhone 17` (as in `scripts/run-all.sh`). If another is needed — variable `IOS_DEVICE`.
   ```bash
   IOS_DEVICE="${IOS_DEVICE:-iPhone 17}"
   UDID=$(xcrun simctl list devices available \
     | awk -F '[()]' -v n="$IOS_DEVICE" '$0 ~ n && $0 !~ /unavailable/ { print $2; exit }')
   [ -z "$UDID" ] && { echo "SKIP: no available simulator matching '$IOS_DEVICE'"; }
   xcrun simctl boot "$UDID" 2>/dev/null || true
   open -a Simulator
   ```
2. **Build + install + launch.** Use `build/ios` as derivedDataPath to reuse the cache between runs.
   ```bash
   IOS_DERIVED=build/ios
   IOS_APP="$IOS_DERIVED/Build/Products/Debug-iphonesimulator/Tether.app"
   IOS_BUNDLE_ID=com.tubetoast.tether.Tether
   xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
     -configuration Debug \
     -destination "platform=iOS Simulator,id=$UDID" \
     -derivedDataPath "$IOS_DERIVED" \
     build > /tmp/smoke-ios-build.log 2>&1
   xcrun simctl install "$UDID" "$IOS_APP"
   xcrun simctl launch "$UDID" "$IOS_BUNDLE_ID" > /tmp/smoke-ios-launch.log 2>&1
   ```
   PASS if build exit=0, install exit=0, launch exit=0. FAIL — tail `/tmp/smoke-ios-build.log`.
3. **mDNS publish.** Poll `dns-sd` for up to 30 sec:
   ```bash
   IOS_NAME=""
   for i in $(seq 1 30); do
     # `dns-sd -B` prints each match as a tab-separated line ending with the instance name;
     # the name is the trailing token after the last tab. The previous regex `[…]*iPhone[…]*`
     # captured leading junk (`tcp.        iPhone 17 Pro`) and broke the subsequent TXT query.
     IOS_NAME=$( ( dns-sd -B _tether._tcp local. & DNSSD=$!; sleep 2; kill $DNSSD 2>/dev/null ) \
       | awk -F'\t' '/_tether._tcp/ && NF>1 { print $NF }' \
       | grep -E 'iPhone|iPad' | head -1 | tr -d '\r' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
     [ -n "$IOS_NAME" ] && break
     sleep 1
   done
   ```
   PASS if `IOS_NAME` is non-empty. FAIL reason — most likely a Local Network Privacy prompt (see "What the skill does NOT check").
4. **TXT publish.** `dns-sd -q "${IOS_NAME}._tether._tcp.local." TXT` for ~3 sec, must return `4 bytes: 03 76 3D 31` (`v=1`). PASS if the binary pattern matched.
5. **Cross-discovery.** Wait up to 30 sec for the iOS peer to appear in Desktop CLI A's log:
   ```bash
   for i in $(seq 1 30); do grep -q "$IOS_NAME" "$LOG_A" && break; sleep 1; done
   ```
   PASS if a line with `IOS_NAME` appeared in log A.

iOS cleanup — in Block 7.

### Block 7: Cleanup

Executed **always**:
- `kill $(cat /tmp/smoke-cliA.pid /tmp/smoke-cliB.pid /tmp/smoke-cliC.pid /tmp/smoke-cliA-keeper.pid /tmp/smoke-cliB-keeper.pid /tmp/smoke-cliC-keeper.pid 2>/dev/null) 2>/dev/null`
- `pkill -f 'com.tubetoast.tether.*\.jar'` (safety net)
- `rm -f /tmp/smoke-cli*-in /tmp/smoke-cli*.log /tmp/smoke-cli*.pid /tmp/smoke-cli*-keeper.pid /tmp/smoke-send.txt /tmp/smoke-android.txt`
- Files in `~/Downloads/Tether/` that we created — clean up by `savedPath` from log A.
- `adb shell rm -f /sdcard/Android/data/com.tubetoast.tether/files/Tether/smoke-android.txt` (or by `SAVED_PATH` if parsed)
- `adb shell am force-stop com.tubetoast.tether`
- `xcrun simctl terminate "$UDID" com.tubetoast.tether.Tether 2>/dev/null || true` (if `UDID` was resolved in Block 5.5)
- `rm -f /tmp/smoke-ios-build.log /tmp/smoke-ios-launch.log`

## Report format

At the end of the run print a markdown report:

```markdown
# Smoke test report

**Date:** YYYY-MM-DD HH:MM
**Branch:** <git branch>
**Commit:** <short sha>
**CLI Jar:** <auto-detected name> (built in Ns)

## Summary

- **PASS:** N
- **FAIL:** M
- **SKIP:** K
- **Total:** N+M+K
- **Verdict:** 🟢 GREEN | 🟡 YELLOW (SKIPs present, FAIL=0) | 🔴 RED (FAIL present)

## Results

| Block | Scenario | Result | Details |
|---|---|---|---|
| Build | cli jar | ✓ PASS | <Ns>, jar=<name> |
| Desktop CLI A | startup + port | ✓ PASS | port=49507, pid=83952 |
| Desktop CLI A | /health | ✓ PASS | "Tether OK" |
| Desktop CLI A | /pair X.509 EC P-256 | ✓ PASS | 91 bytes, DER prefix OK |
| Desktop CLI A | port LISTEN | ✓ PASS | java *:49507 |
| Desktop CLI A | mDNS publish (log) | ✓ PASS | advertising 'SmokeMacA' |
| Desktop CLI A | mDNS publish (dns-sd) | ✓ PASS | SmokeMacA in browse |
| Desktop CLI A | stdin `list` | ✓ PASS | peer printed |
| Desktop↔Desktop | send via CLI | ✓ PASS | savedPath parsed, diff empty |
| Same-name discovery | A/B/C convergence | ✓ PASS | each sees 2 SmokeMac peers in 3s |
| Device name | rename via stdin | ✓ PASS | peer sees new name in <15s |
| Desktop CLI A | graceful `quit` | ✓ PASS | exit in 3s |
| Android | adb device | ✓ PASS | <serial>, model, API |
| Android | installDebug | ✓ PASS | 4s |
| Android | FGS + FileServer | ✓ PASS | port=42367 |
| Android | NSD probing latency | ✓ PASS | 950ms (start→registered) |
| Android | mDNS publish | ✓ PASS | Tether-<MODEL> |
| Android | /health (over WiFi) | ✓ PASS | "Tether OK" |
| Android | cross-discovery | ✓ PASS | 2154ms (launch→peer-on-Desktop) |
| Android | send Desktop→Android | ✓ PASS | savedPath parsed, diff empty |
| Android | force-stop | ✓ PASS | process killed |
| iOS | xcodebuild + install | ✓ PASS | UDID=<...>, 28s |
| iOS | launch | ✓ PASS | pid=<...> |
| iOS | mDNS publish | ✓ PASS | service=<IOS_NAME>, TXT=03 76 3D 31 |
| iOS | cross-discovery | ✓ PASS | seen on Desktop A in 4s |

## Failures

(FAIL details: command, stderr tail, hypothesised cause)

## Manual verification required (not covered by smoke)

- Physical iPhone — install via Xcode, verify cross-discovery with Desktop.
- iOS receive (FileServer.apple — stub) — skipped by design.
- **Android-initiated send (Android → Desktop)** — no CLI on Android, the skill checks the reverse direction.
- Notification "Stop" button on Android — verify tap manually; smoke uses `am force-stop`.
- Sleep/wake real device — `adb input keyevent` ≠ real power state.
- Rotation persistence — on a real device; emulator is insufficient.

## Environment

- OS: Darwin <version> arm64
- Java: <java -version>
- adb device: <serial / model / API> or "none"
- Xcode: <xcodebuild -version | head -1>
- dns-sd: <path> or "not available"
```

## How to invoke

When the user asks to "run smoke":

1. Print a short plan (1–2 lines): "I'll run Desktop CLI via cli jar, Desktop↔Desktop send, Android if a device is connected, native compile. What I don't check — see the report."
2. Run the blocks.
3. Print the report.
4. If verdict is 🔴 — give a recommendation: which block failed and where to look.

Don't ask the user for clarification — the skill must be "zero-question": everything non-automatable goes into Manual verification.

## Edge cases during the run

- **Gradle daemon busy** — don't kill it, it will be reused.
- **CLI jar stale (code changed)** — `cliJar` will rebuild what's needed. Don't run `clean`.
- **Jar name may contain version** — determine dynamically via glob `composeApp/build/libs/tether-cli-*.jar composeApp/build/libs/tether-cli.jar | head -1`. Don't hardcode the filename.
- **`dns-sd` not on macOS** — unavailable on Linux; secondary mDNS check SKIP with reason "dns-sd not available". Primary check (grep CLI log for `mDNS started`) still works.
- **`timeout` absent on macOS** — use pattern `( cmd & PID=$!; sleep N; kill $PID )` instead of `timeout`.
- **FIFO writer keeper died early** — readLine() returns null, CLI exits; check `ps -p $KEEPER`.
- **Android emulator in NAT (10.0.2.x)** — cross-discovery works both ways (multicast passes). QEMU user-mode NAT does not proxy host→guest TCP payload: handshake passes, data doesn't arrive. Send block (step 8) — SKIP at `10.0.2.x`, not FAIL. Health is accessible via `adb forward`. See `docs/knowledge/android-emulator-networking.md`.
- **`ip route` unreliable on some vendors** (ColorOS, MIUI return subnet instead of src) — use `ip addr show wlan0`.
- **Multiple adb devices** — pick the first or fail with a clarification. Don't hang the skill on a specific serial.
- **`savedPath` must always be parsed from the log**, don't guess `$HOME/Downloads/Tether/...` — the downloads directory is user-configurable.

## What NOT to do

- **Don't use `./gradlew :composeApp:run`** — that is the Compose UI, not the CLI.
- **Don't run `allTests`** — that is a different tool. Smoke ≤3 minutes.
- **Don't modify application code** even if you see a problem. Report it in the report, file a separate issue.
- **Don't go into `~/Downloads`** beyond your own files — that is user content.
- **Don't run `./gradlew clean`** — it will eat the cache and slow down the next run.
- **Don't send anything over the network** other than localhost and `$ANDROID_IP` (the latter — only if an adb device is connected).
- **Don't guess file destination paths** — always parse from `[send] OK — ... → <savedPath>`.
