# Roadmap

Three buckets, no dates. Order within a bucket is rough priority.

## MVP

The minimum that makes Tether worth installing. **All four platforms ship together** — the cross-platform promise is the product.

- mDNS discovery on Android, iOS, macOS, Windows. (Linux comes in Post-MVP.)
- Pairing with 4-digit code verification.
- Single-file transfer, streaming (no in-memory buffering).
- Live progress on both sender and receiver, cancel.
- Device list screen + transfer/progress screen on every platform.
- Channel encryption decision made and implemented (see [security.md](security.md)).

Done = a non-technical user can install Tether on their phone (Android/iOS) and macOS/Windows laptop, pair them once, and reliably move files between them on home Wi-Fi.

## Post-MVP

Things the user will ask for soon after MVP, but that the product can survive without at launch.

- Resume after interrupted transfer.
- Multi-file send (queue or zip-stream).
- Background receive on mobile (push / system notification when a file arrives).
- "Show in folder" / "Open" affordances per platform.
- Per-app passcode or biometric lock (depends on outcome of [security.md](security.md) open question).
- Onboarding polish — first-launch experience, permission prompts.

## Later

Directions, not commitments. Some of these may turn out to be wrong; some are paid candidates (see [monetization.md](monetization.md)).

- **Folder sync** — automatic mirroring of selected folders between paired devices. Pro candidate.
- **Multi-peer / group send** — fan out to several devices in one action. Pro candidate.
- **Transfer over the internet** — relay or STUN/TURN for paired devices not on the same LAN. Big architectural shift; explicitly *not* in MVP.
- **Linux desktop** target (JVM-based).
- **Transfer history** — local journal of what went where. Privacy implications under review.

## Non-Goals

For clarity (also in [vision.md](vision.md)):

- Cloud storage / backup.
- Chat / messaging features.
- Account-based device linking across the internet (replaced by pairing on LAN).
- Photo / video compression of any kind.
