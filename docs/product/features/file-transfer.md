# File transfer

**Area:** Transfer / UI
**Status:** `idea`
**GitHub Issues:** [#8](https://github.com/khmelevartem/tether/issues/8) (Android send UI); iOS, Desktop — _tbd_; receive-side UI — _tbd_

---

> Stub. Captures the gap. Flesh out before the feature enters a sprint.

## Why

After picking a device on the [device list](device-list.md), the user needs to actually move a file and see what is happening — both as the sender and as the receiver. Without this Tether cannot deliver its core value: picking a peer with no follow-through is worse than not having a list at all.

This feature also carries Tether's two non-negotiable transport promises (see [vision.md](../vision.md)): **original bytes, untouched** (no compression, no conversion) and **any size** (no in-memory buffering, no silent failures on large files). They apply to both directions and to every platform.

## What it does (sketch)

**On the sender side**

- User picks one or more files via the system picker (Photos, Files, document picker — whatever the OS provides).
- Tether starts sending; a progress screen shows the file name, percentage, and current speed.
- Cancel during transfer is visible and works; partial files do not linger on the receiver.
- On success the user gets a clear acknowledgement; on failure a clear message with a way to retry.

**On the receiver side**

- An incoming transfer surfaces clearly — the user sees who is sending what, and progress.
- When complete, the file lands in a known place; the user can find it without hunting.
- Cancel from the receiver is possible.

**Underlying guarantees** (apply to both sides)

- Files of any size work without out-of-memory errors. Streaming, not buffering.
- Bytes are delivered exactly as the sender provided them — no compression, no conversion, no resizing.
- A failed or cancelled transfer does not leave a partial file pretending to be complete.

## Platform notes

- **Android (sender):** picker via `ActivityResultContracts.GetContent` (or `OpenDocument`); `READ_MEDIA_*` permissions on API 33+ owned by [permissions strategy](permissions-strategy.md).
- **Android (receiver):** for backgrounded receive, foreground service is required — see [permissions strategy](permissions-strategy.md).
- **iOS (sender):** picker source(s) need to be decided — Photos, Files, or both; backgrounding behaviour during transfer is an open question.
- **iOS (receiver):** OS limits on background networking apply; what counts as "delivered" while the app is suspended is open.
- **Desktop:** standard file dialogs; no permission flow.

## Not in this feature

- Resume after interrupted transfer — Post-MVP, see [roadmap.md](../roadmap.md).
- Multi-file send semantics (batching, aggregate progress) — separate feature, see [multi-file-transfer.md](multi-file-transfer.md).
- "Show in folder" / "Open file" affordances after receive — Post-MVP.
- Pairing prompts — owned by [pairing-pin-confirmation.md](pairing-pin-confirmation.md).

## Open product questions

- Background transfer on mobile (app suspended, screen locked) — explicitly out for MVP, or supported with limitations?
- File picker source on iOS — Photos, Files, or both?
- Visible categories of failure (network lost, peer gone, file unreadable) and what each looks like to the user.
- Where exactly does an incoming file land on each platform, and what is the user's first encounter with that location?
- What does "cancelled mid-transfer" leave behind on the receiver — discarded silently, kept as partial, surfaced as "incomplete"?
