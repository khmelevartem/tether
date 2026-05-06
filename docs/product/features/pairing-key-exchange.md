# Pairing — key exchange & trusted device memory

**Area:** Pairing / Security
**Status:** `scoped`
**GitHub Issues:** [#9](https://github.com/khmelevartem/tether/issues/9)

---

## Why

Today any device on the same Wi-Fi can send a file to any other Tether device — there is no notion of trust. For an MVP that goes to non-technical users this is unacceptable: a roommate, a coworker, or any guest on the network can drop arbitrary content uninvited. Tether's promise of "two taps to send" only works if the two devices on either end recognise each other; otherwise the receiver needs a wall of confirmations and Tether stops feeling local and effortless.

This feature establishes the foundation: each device has a stable identity, and once two devices have met, they remember each other. The visible "do you trust this device?" step lives in a separate pairing feature; this one makes that step possible and ensures it only happens once per pair of devices.

## What it does

Every Tether installation has its own stable identity that persists across app restarts. The first time two devices try to talk to each other, they exchange identities and remember the result. From then on, those two devices recognise each other silently — no further prompt, no repeated check.

Reinstalling Tether on a device gives that device a new identity, and the next encounter is treated as a first encounter again — which matches what a user would expect after wiping and reinstalling an app.

## User flows

**Primary flow**

1. The user has Tether installed on two of their devices and has used both at least once.
2. They send a file from one to the other for the **first time**. A confirmation step happens (owned by the next pairing feature).
3. The next time the same two devices meet, the file goes through without asking again.

**Alternative paths**

- **Reinstall.** The user uninstalls and reinstalls Tether on one device. Next time they connect that device to the other one, it is treated as a first encounter, and the confirmation step happens again.
- **Storage failure on the trusting device.** The remembering step quietly fails. The next encounter is treated as a first encounter and the user simply confirms again. No crash, no scary error.
- **Three or more devices.** Memory is per pair: confirming A↔B does nothing for A↔C. Each new pair has its own first encounter.

## What "working" looks like

- After a first successful exchange between two devices, restarting Tether on either device does not undo the recognition — they still know each other.
- Reinstalling Tether on one device causes the next encounter to behave as a first encounter again.
- A stranger's device on the same Wi-Fi cannot push a file to the user without going through the first-encounter step. The receiver always knows when an unfamiliar device is reaching out for the first time.
- Two devices that have never met before always go through the first-encounter step, regardless of which one initiates.

## Not in this feature

- The visible "do you trust this device?" prompt and the 4-digit code the user compares — owned by [#10](https://github.com/khmelevartem/tether/issues/10) and [#11](https://github.com/khmelevartem/tether/issues/11).
- Encryption of the file transfer itself — separate, see [security.md](../security.md).
- A "forget device" or "manage trusted devices" screen — comes after MVP.
- Stronger storage on Apple platforms (Keychain). The first version uses default per-platform storage; Keychain is a follow-up.

## Open product questions

- What does the user see if their device list shows a peer they have already trusted versus one they have never met? A subtle marker ("known device") could be useful, but it is not obviously needed for MVP and it could mislead if storage is ever wiped silently.
- After reinstall on the partner side, the next encounter will look identical to a brand-new device. Should the receiving side surface "this looks like a device that was previously known under a different identity"? Probably no for MVP — but worth revisiting once we see how often reinstalls happen in practice.
