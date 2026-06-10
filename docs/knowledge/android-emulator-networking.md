# Android emulator — networking constraints

QEMU user-mode (SLIRP) networking applies when running the Android emulator with default settings. The emulator advertises its own `wlan0` IP (`10.0.2.x`, typically `10.0.2.16`) via NSD/mDNS.

## What works

- **mDNS discovery in both directions.** Desktop CLI sees the emulator peer; the emulator sees Desktop peers.
- **Android-initiated outbound TCP.** Connections from the emulator to host services work normally.
- **`/health` endpoint via `adb forward`.** Use `adb forward tcp:<host-port> tcp:<guest-port>` and hit `localhost:<host-port>` from the host. Response is immediate.

## What does not work

**Host→emulator TCP from advertised IP.** QEMU user-mode (SLIRP) networking blocks all unsolicited inbound connections to the guest by default; only ports exposed via `hostfwd` or `adb forward` are reachable. The emulator's `wlan0` IP (`10.0.2.x`) is therefore unreachable from the host without an explicit forward, even though it appears in mDNS announcements. Effect: `send` from Desktop CLI to a discovered emulator peer hangs (the connection cannot be established or terminates with no payload received, depending on environment).

This is a documented architectural constraint of QEMU user-mode networking (SLIRP), not a Tether bug.

## Spurious NSD lost/found cycles

Android NSD on the emulator emits periodic `onServiceLost` + `onServiceFound` events for stably-published peers (Bonjour mDNSResponder re-announcements read as removal). No fix in this codebase — environment artefact.

## Workarounds for dev work

- **Endpoint sanity checks (e.g. `/health`):** `adb forward tcp:<host-port> tcp:<guest-port>` then `curl http://localhost:<host-port>/health`.
- **End-to-end file send:** use a real device. There is no in-app workaround; CLI does not support address override.

## Detection

Emulator `wlan0` IP is in `10.0.2.0/24`. Check with `adb shell ip addr show wlan0`.

## Smoke-test behavior

The send-block (Block 4 step 6) skips when `ANDROID_IP` starts with `10.0.2.`. Cross-discovery (step 5) is not skipped — it works. See `.claude/skills/smoke-test/SKILL.md` Block 4.
