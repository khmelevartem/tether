# Vision & Principles

## Concept

Tether is a peer-to-peer file transfer app for people who own devices on different operating systems. The pain it solves: today, sending a photo from an Android phone to a MacBook means a messenger that compresses it, an email that limits it, or a cable. Tether replaces that with two taps on the same Wi-Fi — no cloud, no account, no compression, no platform lock-in.

The closest mental model the user already has is "send via Telegram / WhatsApp," and that's the frame Tether displaces. Unlike a messenger, Tether ships the original file untouched and never routes it through anyone's servers.

## Mission

Move files between any two devices on the same Wi-Fi as fast and as simply as possible — regardless of which operating systems they run.

## Principles

When a tradeoff arises, decide in this order:

1. **Cross-platform is the product.** A feature that doesn't work on all four targets isn't a feature, it's a half-built bridge. We ship to Android, iOS, macOS, and Desktop together.
2. **Original bytes, untouched.** No compression, no resizing, no conversion. Ever.
3. **No accounts, no phone numbers, no contacts.** Identity on the network is enough. The user shouldn't have to look up who owns a device — they pick from a list of devices on their Wi-Fi.
4. **Local-first, no cloud.** Transfers go directly between devices. If the LAN can't carry it, we say so honestly — we do not silently fall back to a relay.
5. **Two taps to send.** Pick device, pick file. Anything that adds a step needs to earn it.

## Goals

### Now (MVP)

- All four platforms ship working: Android, iOS, macOS, Desktop JVM.
- A non-technical user pairs their phone and laptop once and reliably moves files at home.
- Discovery, pairing, and single-file streaming transfer work end to end with visible progress.

See [roadmap.md](roadmap.md) for the full MVP cut.

### Later

- Folder sync and multi-peer send for power users (probably paid — see [monetization.md](monetization.md)).
- Resume after interrupted transfers.
- Optional transfer over the internet for paired devices not on the same LAN.

## Non-Goals

To keep scope honest, Tether is explicitly *not*:

- A cloud storage service. Files don't live on Tether infrastructure — there is no Tether infrastructure.
- A messenger. No chat, no reactions, no statuses, no presence beyond "device is on the network."
- A Dropbox-style sync product in MVP. Sync arrives later, scoped narrowly, as a Pro feature.
- A general-purpose remote-access tool. We move files; we don't expose drives, shells, or APIs.
- A platform-native experience per OS. Tether looks the same on Android, iOS, macOS, and Desktop on purpose.
