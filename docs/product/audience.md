# Target Audience

## Primary User

A non-technical person who owns devices across more than one ecosystem. The canonical example: an Android phone and a MacBook at home. Or an iPhone and a Windows PC. Or a tablet on one OS and a laptop on another.

They are *not* a developer, *not* a sysadmin, *not* a privacy activist. They don't care about how the transfer works — they care that the photo they took on their phone shows up on their laptop in full quality without effort.

Technical level: comfortable installing apps, comfortable joining Wi-Fi, not comfortable reading network settings or troubleshooting permissions.

The trigger: they took a photo / recorded a video / received a document on one device and need it on another to do something with it (edit, attach, print, archive).

## Context of Use

**Primary scenario — at home, between their own devices.** Phone → laptop is the dominant case. Both devices are already on the same home Wi-Fi. The user wants the file to "be on the other device" with as little ceremony as possible.

**Secondary scenario — sharing with someone nearby.** A friend or colleague in the same Wi-Fi. Less frequent, but the same flow. Tether should not block this case, but it doesn't optimize for it either — pairing is per-device-pair and persists, which is right for "my devices" and tolerable for "occasionally a friend's."

**Current alternatives and why they fall short:**

| Today's tool | Why it fails |
|--------------|--------------|
| Telegram / WhatsApp "send to self" | Compresses photos and video. Limits file size. Routes through a server. |
| AirDrop | Apple-only. Useless when one of the user's devices is Android or Windows. |
| Email to self | Size limits. Lossy for video. Slow round-trip. |
| USB cable | Permission dialogs, missing drivers, wrong cable. Doesn't help phone↔phone. |
| Cloud (Drive / Dropbox) | Upload and download a file just to move it across the room. Quotas. Account setup. |

## Key Jobs to Be Done

- When **I have a photo or video on my phone that I want to edit on my laptop**, I want to send it without compression, so I can keep the original quality.
- When **I want to move a file between my own devices on different operating systems**, I want a single tool that works on both, so I don't have to maintain different workflows per device pair.
- When **I'm sending something to a friend in the same room**, I want to do it without adding them as a contact in any app, so the act of sharing is private and disposable.

## What They Don't Care About

- Chat, reactions, message history — they're moving a file, not talking.
- Account portability across networks. They're at home; their devices are right there.
- Server-side features (search, backup, sharing links). Tether has no server side, by design.
- Configurable protocols, custom ports, advanced network settings.
- Per-OS native look. They want the app to feel familiar to *Tether*, not to each OS — they switch between their devices and want continuity.
