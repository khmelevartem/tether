# Roadmap

Three buckets, no dates. Order within a bucket is rough priority.

## MVP

The minimum that makes Tether worth installing. **All four platforms ship together** — the cross-platform promise is the product.

- mDNS discovery on Android, iOS, macOS, Windows. (Linux comes in Post-MVP.)
- Pairing with SAS verification (compare a short code on both devices).
- Multi-file transfer, streaming (no in-memory buffering). Multi-select picker, sequential per-file transfer over a single session, aggregate progress, cancel.
- Live progress on both sender and receiver, cancel.
- Device list screen + transfer/progress screen on every platform.
- Settable device name (implicit in pairing/discovery; full settings screen comes in Post-MVP).
- Channel encryption decision made and implemented (see [security.md](../security/README.md)).
- Installable and published on all four platforms — signed builds from a legitimate channel (Play, App Store, signed desktop installers) plus a public landing page (epic [#430](https://github.com/khmelevartem/tether/issues/430)).

Done = a non-technical user can install Tether on their phone (Android/iOS) and macOS/Windows laptop, pair them once, and reliably move files between them on home Wi-Fi.

## Post-MVP

Things the user will ask for soon after MVP, but that the product can survive without at launch.

- Resume after interrupted transfer.
- Settings screen — device name, default save folder, UI theme, "share Tether" link.
- Onboarding polish — first-launch experience, permission prompts.

## Later

Directions, not commitments. Some of these may turn out to be wrong; some are paid candidates (see [monetization.md](monetization.md)).

- **Folder sync** — automatic mirroring of selected folders between paired devices. Pro candidate.
- **Multi-peer / group send** — fan out to several devices in one action. Pro candidate.
- **Linux desktop** target (JVM-based).
- **Transfer history** — local journal of what went where. Privacy implications under review.

## Non-Goals

For clarity (also in [vision.md](vision.md)):

- Cloud storage / backup.
- Chat / messaging features.
- Account-based device linking across the internet (replaced by pairing on LAN).
- Photo / video compression of any kind.
