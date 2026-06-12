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
- **Packaged desktop installers** (jpackage `.dmg` / `.msi` / `.deb` and the `.app` image) — not covered. The Desktop block runs the CLI fat-jar on the full system JDK; the installer instead bundles a jlinked, stripped runtime and launches the Compose UI, a path that surfaces failures the jar never hits. Build and launch the installer manually — see [docs/knowledge/jpackage-jlink-modules.md](../../../docs/knowledge/jpackage-jlink-modules.md).
- **iOS Local Network Privacy prompt** — on the first app launch on the simulator iOS may show "Allow Local Network access". Without Allow, `NSNetService.publish()` silently fails. If the iOS block fails on publish — check the prompt manually, grant Allow, restart the smoke.
- **Android-initiated send (Android → Desktop)** — Android has no programmatic send trigger (intent / UI button / broadcast). The skill checks the reverse direction: Desktop → Android via CLI `send`.
- **Tapping the Notification "Stop" button** — replaced by `am force-stop` or broadcast. That the *button is rendered and works* — verify manually.
- **Sleep/wake of a real device** — `adb shell input keyevent SLEEP/WAKEUP` is an approximation, not a real power state.
- **Rotation effects on FGS survivability** — emulator ≠ real device.

All these items must appear in the **"Manual verification required"** section of the report.

## Shared environment (`smoke-env.sh`)

Blocks run in separate shells and coordinate only through fixed filesystem paths, so every block sources `smoke-env.sh` at the top to re-derive identical, worktree-scoped values. From the worktree root it derives a per-worktree scratch dir under `/tmp` and, beneath it, the per-instance fifo / log / pid / keeper paths, the iOS build/launch logs, and the resolved cli jar. Because the namespace is keyed on the worktree, two runs in different worktrees never share scratch state, PID bookkeeping, or kill scope. It also exposes a basename prefix for sent files (so receiver-dir cleanup under the shared `$HOME/Downloads/Tether` targets only this run) and helpers to kill / detect this worktree's CLI instances.

Kill is matched by the worktree's **jar path**, not by a package-name substring — the CLI runs as `java -jar …/tether-cli.jar`, so a package-name pattern never matches it.

## Starting the CLI

The FIFO (`$FIFO_A` etc.) keeps stdin open for `list`, `send`, `quit` commands. All blocks that launch a CLI instance follow this pattern — see `block-1-desktop-cli-a.sh` for the canonical form.

Desktop CLI instances are launched with `TETHER_LOG_DEBUG=true` so subsystem logger lines are captured in the log and available for assertions and diagnostics. Product output (the bracketed `[…]` status lines and the startup banner) is echoed regardless of this flag.

## Shell idioms for blocks

Blocks run under `set -euo pipefail`. Commands that exit nonzero as a *normal* result — `grep` / `grep -c` with no match, glob expansion that matches nothing — then abort the script or feed a wrong value downstream. Two idioms keep blocks robust:

- **Counting occurrences:** capture with `… || true`, then default with `${VAR:-0}`. Never `… || echo 0` — on an empty match `grep -c` already prints `0` *and* exits nonzero, so the fallback appends a second line and the variable becomes two values, breaking later arithmetic.
- **Resolving a path that may be absent:** guard the lookup so an empty result is an explicit branch, not a pipeline abort; do not rely on a bare glob or `ls` succeeding.
- **Parsing a field from a log line:** match the field by its label, not by line position — a trailing column (a path, a suffix) shifts what an end-of-line pattern captures.

## Run plan

**Primary invocation — `./run-all.sh`.** It drives every block back-to-back in one pass, tees a greppable consolidated log (`===== BLOCK<n> =====` headers) to stdout and `/tmp/smoke-results-<id>.log`, guards the iOS block when Xcode/simulator is absent, and runs cleanup via an `EXIT` trap. Launch it as **one** task and read the result log to synthesise the report — do not invoke blocks one-by-one with waits in between: each CLI's FIFO keeper is `sleep 600`, so a CLI dies ~10 min after its block started, and a spread-out run kills CLI A mid-flight (later blocks then FAIL against a dead instance). A watchdog aborts the run (and still cleans up) after `SMOKE_DEADLINE` seconds — default 540, override via the env var for a slower machine.

The individual `block-*.sh` scripts below remain runnable on their own for targeted re-runs and debugging. A block failure does not prevent subsequent blocks from running. Cleanup (`block-7-cleanup.sh`) runs **always** — `run-all.sh` triggers it on `EXIT`; if you run blocks by hand, invoke it yourself even after earlier FAILs.

All scripts live in `.claude/skills/smoke-test/` and are self-contained — run them from that directory or the repo root.

### Running blocks selectively

To re-run or debug part of the suite, run the `block-*.sh` scripts directly — but a block depends on the **live CLI instances** earlier blocks left running, not just on those scripts having executed. So run the target's prerequisite prefix first, then the target. Prerequisites:

| Target block | Needs alive first | Minimal prefix |
|---|---|---|
| 1 (CLI A) | jar built | `0 → 1` |
| 2.1, 4 (Android), 5 (iOS), 6 (quit) | CLI A | `0 → 1 → <target>` |
| 2.2, 2.3, 3.5 | CLI A + B | `0 → 1 → 2.1 → <target>` |
| 3 (same-name) | CLI A + B | `0 → 1 → 2.1 → 3` |
| 3.1, 3.2 | CLI A + B + C | `0 → 1 → 2.1 → 3 → <target>` |

Two constraints when running by hand:

- **Keeper window.** Each CLI's FIFO keeper is `sleep 600`, so an instance self-exits ~10 min after its block started. Finish the selective sequence well inside that window, or a later block FAILs against a dead instance.
- **Cleanup is yours.** The `EXIT`-trap cleanup and the watchdog live only in `run-all.sh`. When running blocks by hand, end with `./block-7-cleanup.sh` (it kills this worktree's instances and removes the scratch dir) even after a FAIL, or instances and `$SMOKE_DIR` leak until the next `block-0`.

Leaving the instances up between hand-run blocks is the point of this mode — keep CLI A (and B/C) alive and poke at successive scenario blocks, then clean up once.

### Block 0: Preparation

Run: `./block-0-preparation.sh`

Kills lingering CLI instances (scoped to this worktree) and builds the CLI jar; subsequent blocks derive `$JAR` via smoke-env.sh.

FAIL → all remaining blocks SKIP with reason "cli jar build failed".

### Block 1: Desktop CLI (instance A)

Run: `./block-1-desktop-cli-a.sh`

Launches CLI A (`SmokeMacA`, random port), then checks:

1. **Startup** — port parsed, java pid alive. PASS if both.
2. **`/health`** — must return `Tether OK`.
3. **`/pair` — X.509 EC P-256 shape.** Response must be 91 bytes, first byte `0x30`, byte 26 `0x04`. Verifies real key material, not a placeholder.
4. **Port LISTEN** — java listener shown by `lsof`.
5. **mDNS publish (log)** — the CLI's startup log shows that mDNS advertising started for its configured instance name.
6. **mDNS publish (dns-sd, optional)** — `dns-sd -B` browse. SKIP if `dns-sd` unavailable (Linux).
7. **stdin `list`** — must produce a `[list]` or `[peers]` line.

CLI A stays alive through the Android and iOS blocks (they need it for cross-discovery). Graceful quit — Block 6, deferred to just before cleanup.

### Block 2: Desktop ↔ Desktop send (via CLI)

**Important:** send must go via the CLI `send` command, not via `curl POST /upload`.

Scenario 2.1 starts CLI B (`SmokeMacB`) and waits for mutual mDNS discovery; 2.2 and 2.3 assume B is still alive. Each scenario is independently re-runnable (re-running 2.2 or 2.3 alone requires B to already be running).

#### Scenario 2.1 — single-file send

Run: `./block-2.1-single-file-send.sh`

Sends one file from A to B. PASS if `[send] done` appears in A's log AND the file lands at `$HOME/Downloads/Tether/` byte-identical to the source.

#### Scenario 2.2 — multi-file send (3 files in one `send` command)

Run: `./block-2.2-multi-file-send.sh`

Sends 3 files in one command. PASS if `[send] done — 3/3 sent` appears AND all 3 files land byte-identical.

#### Scenario 2.3 — `retry` happy path

Run: `./block-2.3-retry.sh`

Stops B to provoke `[send] error`, restarts B with the same `--config-dir` it started with in 2.1, then issues `retry SmokeMacB`. B's `--config-dir` identity (name + fingerprint) survives the stop→restart, so A's transfer engine tracks it as the same peer and the failed transfer retains its terminal state for `retry` to resume from. PASS if the file lands byte-identical after retry.

#### Scenario 2.4 — exit code on `quit`

Verified in Block 6 — `lastExit` accumulates per `send`/`retry`; the last successful send was AllSent → expected exit code 0.

### Block 3: Same-name discovery

Run: `./block-3-same-name-discovery.sh`

Launches a third instance (`SmokeMacA` — same name as A) to verify mDNS conflict-rename: each of A/B/C must see ≥ 2 unique SmokeMac peers within 20 s.

FAIL → attach the last `[peers]` lines from all three logs in Details.

Cleanup of instance C — in Block 7.

### Block 3.1: Peer dedup (same instance under multiple aliases)

Run: `./block-3.1-peer-dedup.sh`

Regression guard for #346. macOS `mDNSResponder` canonicalises duplicate service names by appending a numeric suffix to the conflicting name. A peer receiving both the pre-rename and post-rename announces for the same instance must collapse them into one entry — assertion: in each CLI's last `[peers]` line, no two peers share the same `host:port`.

Prerequisite: Block 3 (three CLIs alive — A, B, and C reusing A's name). FAIL → attach the last `[peers]` lines from all three logs in Details.

### Block 3.2: Same-name distinguishability

Run: `./block-3.2-same-name-distinct.sh`

The mDNS-canonical `(N)` suffix recorded on discovery must survive the subsequent `/hello` exchange — assertion: in each CLI's last `[peers]` line, no two peers share the same display name. Complements Block 3.1.

Prerequisite: Block 3 (three CLIs alive — A, B, and C reusing A's name). FAIL → attach the last `[peers]` lines from all three logs in Details.

### Block 3.5: Device name rename

Run: `./block-3.5-rename.sh`

Sends `name RenamedA` to A via stdin; B must see the new name via mDNS republish within 15 s.

### Block 4: Android (conditional)

Run: `./block-4-android.sh`

SKIP if no adb device connected. If present:

1. `installDebug`
2. Start activity, wait for `NSD service registered`.
3. Parse Android port and IP.
4. `/health` sanity.
5. Cross-discovery with timing (ms from NSD ready to peer appearing on Desktop A).
6. Send Desktop → Android via CLI `send`. SKIP if emulator with QEMU NAT (`10.0.2.x`) or Android peer not discovered.
7. `force-stop`.

### Block 5: iOS simulator runtime

Run: `./block-5-ios.sh`

1. Resolve + boot simulator (default `iPhone 17`; override via `IOS_DEVICE` env var).
2. `xcodebuild`, install, launch.
3. mDNS publish — `dns-sd -B` for up to 30 s.
4. TXT record — must carry an `fp=` field (`66 70 3D`); length-agnostic, since the fingerprint is the hex SHA-256 of the public key.
5. Cross-discovery — iOS peer must appear in Desktop A's log within 30 s.
6. `/health` on the real iOS bundle — port discovered via `lsof` on the host loopback.
7. `/pair` X.509 EC P-256 SPKI shape (91 bytes, `[0]=0x30`, `[26]=0x04`). Load-bearing gate for the class of Apple-Keychain regression that unit tests cannot reach — `simctl spawn` binaries have no app identity, so `SecItem*` returns "unavailable" regardless of correctness. See `docs/knowledge/apple-platform.md`.
8. Keychain persistence across cold launches — restart the app, fetch `/pair` again, compare the public key byte-for-byte.

iOS cleanup — in Block 7.

### Block 6: Graceful quit of instance A — and `lastExit` propagation

Run: `./block-6-graceful-quit.sh`

Runs after the Android and iOS blocks — both need instance A alive for cross-discovery, so A's graceful quit is deferred to just before cleanup.

Sends `quit` to A, waits up to 8 s, checks exit. PASS if the process exited.

Checks exit code = 0 (last send was AllSent after the retry scenario). The exit code is read from the per-run exit file (`$EXIT_A`) written by block-1's launch-wrapper subshell — `wait` on a detached PID returns 127 and cannot be used. If the exit file never appears — FAIL. If the process had to be force-killed — exit-code check SKIP.

### Block 7: Cleanup

Run: `./block-7-cleanup.sh` — **always**.

Kills this worktree's CLI instances and keepers (via `smoke_kill_instances`), removes the per-run `$SMOKE_DIR` (fifos, logs, PIDs, sent source files, iOS build/launch logs), this run's received files in `$HOME/Downloads/Tether/` (matched by `$SMOKE_SEND_PREFIX`), Android device files, and terminates the iOS simulator app. Scoped to this worktree, so a concurrent smoke run in another worktree is left untouched.

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
| Desktop↔Desktop | single-file send | ✓ PASS | file lands in receiver downloads, diff empty |
| Desktop↔Desktop | multi-file send (3 files) | ✓ PASS | `[send] done — 3/3 sent`, all 3 diff empty |
| Desktop↔Desktop | retry after error | ✓ PASS | file lands after `retry`, diff empty |
| Desktop CLI A | exit code on `quit` | ⊘ SKIP | unobtainable cross-shell (wait=127); graceful exit passed |
| Same-name discovery | A/B/C convergence | ✓ PASS | each sees 2 SmokeMac peers in 3s |
| Peer dedup (#346) | no host:port collisions | ✓ PASS | A=0 B=0 C=0 duplicate (host,port) entries |
| Device name | rename via stdin | ✓ PASS | peer sees new name in <15s |
| Desktop CLI A | graceful `quit` | ✓ PASS | exit in 3s |
| Android | adb device | ✓ PASS | <serial>, model, API |
| Android | installDebug | ✓ PASS | 4s |
| Android | FGS + FileServer | ✓ PASS | port=42367 |
| Android | NSD probing latency | ✓ PASS | 950ms (start→registered) |
| Android | mDNS publish | ✓ PASS | Tether-<MODEL> |
| Android | /health (over WiFi) | ✓ PASS | "Tether OK" |
| Android | cross-discovery | ✓ PASS | 2154ms (launch→peer-on-Desktop) |
| Android | send Desktop→Android | ✓ PASS | file lands in app Tether dir, diff empty |
| Android | force-stop | ✓ PASS | process killed |
| iOS | xcodebuild + install | ✓ PASS | UDID=<...>, 28s |
| iOS | launch | ✓ PASS | pid=<...> |
| iOS | mDNS publish | ✓ PASS | service=<IOS_NAME>, TXT carries fp= |
| iOS | cross-discovery | ✓ PASS | seen on Desktop A in 4s |
| iOS | /health (real bundle) | ✓ PASS | port=55171, "Tether OK" |
| iOS | /pair X.509 EC P-256 | ✓ PASS | 91 bytes via real Keychain |
| iOS | Keychain persistence | ✓ PASS | publicKey identical across cold launches |
| Cleanup | teardown self-check | ✓ PASS | no CLI processes or `$SMOKE_DIR` left after block-7 |

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
2. Run `./run-all.sh` as one task, then read `/tmp/smoke-results-<id>.log` (or the task output).
3. Synthesise and print the report from the result log.
4. If verdict is 🔴 — give a recommendation: which block failed and where to look.

Don't ask the user for clarification — the skill must be "zero-question": everything non-automatable goes into Manual verification.

## Edge cases during the run

- **Gradle daemon busy** — don't kill it, it will be reused.
- **CLI jar stale (code changed)** — `cliJar` will rebuild what's needed. Don't run `clean`.
- **Jar name may contain version** — determine dynamically via glob. Don't hardcode the filename.
- **`dns-sd` not on macOS** — unavailable on Linux; secondary mDNS check SKIP with reason "dns-sd not available". Primary check (grep CLI log for `mDNS started`) still works.
- **`timeout` absent on macOS** — use pattern `( cmd & PID=$!; sleep N; kill $PID )` instead of `timeout`.
- **FIFO writer keeper died early** — readLine() returns null, CLI exits; check `ps -p $KEEPER`. The keeper is `sleep 600`, so a CLI also self-exits ~10 min after launch; a send/discovery FAIL against a CLI that was alive earlier usually means the run dragged past that window (see Run plan — run back-to-back).
- **Android emulator in NAT (10.0.2.x)** — cross-discovery works both ways (multicast passes). QEMU user-mode NAT does not proxy host→guest TCP payload: handshake passes, data doesn't arrive. Send block — SKIP at `10.0.2.x`, not FAIL. Health is accessible via `adb forward`. See `docs/knowledge/android-emulator-networking.md`.
- **`ip route` unreliable on some vendors** (ColorOS, MIUI return subnet instead of src) — use `ip addr show wlan0`.
- **Multiple adb devices** — pick the first or fail with a clarification. Don't hang the skill on a specific serial.
- **Receiver downloads path** — Desktop receiver writes to `$HOME/Downloads/Tether/` by default; smoke walks that dir by filename. On Android, the location is app-private — locate by basename under the app's Tether dir, not by parsed path.
- **Terminal output format** — `[send] done — N/N sent` (success), `[send] partial — N/M sent` (partial), `[send] error — <reason>` (failure). No `savedPath` in the log.

## What NOT to do

- **Don't use `./gradlew :composeApp:run`** — that is the Compose UI, not the CLI.
- **Don't run `allTests`** — that is a different tool. Smoke ≤3 minutes.
- **Don't modify application code** even if you see a problem. Report it in the report, file a separate issue.
- **Don't go into `~/Downloads`** beyond your own files — that is user content.
- **Don't run `./gradlew clean`** — it will eat the cache and slow down the next run.
- **Don't send anything over the network** other than localhost and `$ANDROID_IP` (the latter — only if an adb device is connected).
- **Don't guess file destination paths** — verify by filename: walk `$DOWNLOADS_B` (Desktop receiver) or the app-private Tether dir (Android) for the expected filename, then diff against the source.
