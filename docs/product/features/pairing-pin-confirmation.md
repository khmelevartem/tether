# Pairing — PIN confirmation UI

**Area:** Pairing / UI
**Status:** `idea`
**GitHub Issues:** [#11](https://github.com/khmelevartem/tether/issues/11) (Android); iOS, Desktop — _tbd_

---

> Stub. Captures the gap. Flesh out before the feature enters a sprint.

## Why

The pairing handshake (see [pairing-key-exchange.md](pairing-key-exchange.md) and the upcoming PIN-computation feature) produces a 4-digit code that both sides have to compare. Without a visible UI to show that code and accept/reject the pair, the security guarantee from key exchange does not actually reach the user. This screen is the moment a non-technical user *sees* that Tether is asking permission, not just sending bytes.

## What it does (sketch)

- A modal/dialog shows the 4-digit code prominently and the name of the other device.
- Two clear actions: accept and reject. Accept completes the pair and lets the transfer proceed; reject ends the handshake.
- A timeout cancels the prompt automatically if neither side acts.
- Once a pair is established, this screen never appears again for those two devices (until one of them reinstalls — see [pairing-key-exchange.md](pairing-key-exchange.md)).

## Platform notes

- **Android:** must survive screen rotation without resetting the timeout.
- **iOS:** look matches Android by default; deviation only if the platform demands it (e.g. system sheet vs. modal dialog).
- **Desktop:** dialog window; same content.

## Open product questions

- Exact timeout duration (issue #11 currently proposes 30 s).
- What does the *other* side see while the user is deciding — a "waiting for confirmation…" state, or nothing?
- How is "rejected by other device" surfaced on the initiator side?
