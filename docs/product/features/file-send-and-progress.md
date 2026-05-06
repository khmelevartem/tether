# File send and progress

**Area:** UI / Transfer
**Status:** `idea`
**GitHub Issues:** [#8](https://github.com/khmelevartem/tether/issues/8) (Android); iOS, Desktop — _tbd_

---

> Stub. Captures the gap. Flesh out before the feature enters a sprint.

## Why

After picking a device on the [device list](device-list.md), the user needs to actually send a file and see what is happening. Without this feature Tether cannot deliver its core value on any platform — picking a peer with no follow-through is worse than not having a list at all.

## What it does (sketch)

- User picks a file via the system picker (Photos, Files, document picker — whatever the OS provides).
- Tether starts sending; a progress screen shows the file name, percentage, and current speed.
- Cancel during transfer is visible and works; partial files do not linger on the receiver.
- On success the user gets a clear acknowledgement; on failure a clear message with a way to retry.

## Platform notes

- **Android:** picker via `ActivityResultContracts.GetContent` (or `OpenDocument`); needs `READ_MEDIA_*` permissions on API 33+.
- **iOS:** picker source(s) need to be decided — Photos, Files, or both; backgrounding behaviour during transfer is an open question.
- **Desktop:** standard file dialog; no permission flow.

## Open product questions

- Background transfer on mobile (app suspended, screen locked) — explicitly out for MVP?
- File picker source on iOS — Photos, Files, or both?
- Visible categories of failure (network lost, peer gone, file unreadable) and what each looks like to the user.
