# Pairing — first-encounter verification and trusted recognition

**Area:** Pairing / Security
**Status:** `scoped`
**GitHub Issues:** [#9](https://github.com/khmelevartem/tether/issues/9) (key exchange & trusted device memory), [#10](https://github.com/khmelevartem/tether/issues/10) (handshake & PIN computation, CLI flow), [#11](https://github.com/khmelevartem/tether/issues/11) (Android PIN confirmation UI); iOS, Desktop UI — _tbd_

---

## Why

Today any device on the same Wi-Fi can send a file to any other Tether device — there is no notion of trust. For an MVP that goes to non-technical users this is unacceptable: a roommate, a coworker, or any guest on the network can drop arbitrary content uninvited. Tether's promise of "two taps to send" only works if the two devices on either end recognise each other; otherwise the receiver needs a wall of confirmations and Tether stops feeling local and effortless.

[vision.md](../vision.md) names this as one MVP commitment: *"Pairing with 4-digit code verification."* This feature is that commitment in full — the cryptographic foundation, the PIN that humans compare, and the UI that asks them to compare it.

## What it does

The first time two devices meet, both show the same 4-digit code. The user compares the codes on both screens and confirms on each — two taps. From that moment on the two devices recognise each other: every subsequent connection between them happens silently, no further prompt, no repeated check.

A device that has never paired with this one is always treated as a first encounter, regardless of who initiates. Reinstalling Tether on either device gives that device a new identity, so the next encounter starts over from the PIN screen — which matches what a user would expect after wiping and reinstalling an app.

The user never sees keys, never types a code into a field, never picks an algorithm. They see two short numbers, confirm they match, and Tether remembers.

## User flows

**Primary flow — first encounter**

1. User on device A initiates a transfer to device B from the [device list](../device-list/spec.md).
2. Both A and B show a dialog with the same 4-digit code and the name of the other device.
3. User compares the codes. They match → user taps "Confirm" on both sides.
4. The dialog closes; the transfer proceeds.
5. Tether persists the pair on both devices.

**Subsequent encounter**

1. User on device A initiates a transfer to device B again.
2. No dialog. Transfer just starts.

**Alternative paths**

- **Codes do not match (potential MITM).** User taps "Reject" on either side. Both sides surface the rejection clearly — initiator sees "device declined the connection"; the prompt closes on the other side too.
- **Reinstall on one side.** That device has a new identity. The next encounter is a first encounter again — both see the dialog with a fresh code.
- **Timeout.** If neither user acts within a fixed window (current proposal: 30 s), the prompt closes with a "timed out" message on both sides; no pairing happens, the transfer is cancelled.
- **Storage failure on the trusting device.** The remembering step quietly fails. The next encounter is treated as a first encounter and the user simply confirms again. No crash, no scary error.
- **Three or more devices.** Memory is per pair: confirming A↔B does nothing for A↔C. Each new pair has its own first encounter.

## What "working" looks like

- The first transfer between two devices always shows the dialog with a 4-digit code on **both** ends, and the codes match.
- Tapping "Confirm" on both sides lets the file go through; tapping "Reject" on either side stops it cleanly with a clear message on the other side.
- After a successful pair, restarting Tether on either device does not undo the recognition — they still know each other.
- Reinstalling Tether on one device causes the next encounter to behave as a first encounter again.
- A stranger's device on the same Wi-Fi cannot push a file to the user without going through the dialog. The receiver always knows when an unfamiliar device is reaching out for the first time.
- Two devices that have never met before always go through the dialog, regardless of which one initiates.
- The user never sees raw keys, hex strings, or algorithm names. The whole experience is "compare two numbers, tap Confirm".

## Platform notes

- **Android:** dialog must survive screen rotation without resetting the timeout.
- **iOS:** dialog look matches Android by default; deviation only if the platform demands it (system sheet vs. modal).
- **Desktop:** dialog window; same content.
- **CLI runner (developer-only):** during pairing, the 4-digit code is printed to stdout and confirmation is via `y`/`n` on stdin. This is a debugging affordance for the developer, not a user-facing flow.

## Not in this feature

- Channel encryption of file transfers — separate, see [security.md](../security.md).
- A "forget device" or "manage trusted devices" screen — comes after MVP.
- Stronger storage on Apple platforms (Keychain). The first version uses default per-platform storage; Keychain is a follow-up.
- Pairing of more than two devices in one action (group pairing) — out of scope.
- Recovering pairing when one device's identity drifts (e.g. OS-level keychain reset without app reinstall) — same path as reinstall, no special handling.

## Open product questions

- Exact timeout duration. 30 s is the current proposal; 15 s might be enough; 60 s might be friendlier for non-technical users on the phone.
- What does the *waiting* side see while the other user is still deciding — a "waiting for confirmation…" state, or nothing? If nothing, the second person to look at their screen finds a half-closed flow.
- Should the device list mark previously-paired peers somehow ("known device") so the user knows when to expect the dialog? Could mislead if storage is ever wiped silently.
- After reinstall on the partner side, the next encounter looks identical to a brand-new device. Should the receiving side surface "this looks like a device that was previously known"? Probably no for MVP — but worth revisiting once we see how often reinstalls happen.
- How is "rejected by other device" surfaced on the initiator side — a snackbar, a dialog, a quiet log entry?
