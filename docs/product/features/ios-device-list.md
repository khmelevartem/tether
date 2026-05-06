# iOS device list screen

**Area:** UI
**Status:** `idea`
**GitHub Issues:** _tbd_

---

> Stub. Captures the gap. Flesh out before the feature enters a sprint.

## Why

iOS is an MVP target — the cross-platform promise is the product. The Android device list (see [android-device-list.md](android-device-list.md)) gives the user a way to pick a peer; iOS has no equivalent yet. Without this screen the iOS app is unusable.

## What it does (sketch)

- Same role as the Android device list: live list of peers found via mDNS, with a clear "searching" state.
- Looks the same as the Android version on purpose — Tether is one product, not five.
- Tapping a row leads to the iOS send flow (separate feature).

## Open product questions

- Local Network permission prompt timing — at app launch, or only when the user opens this screen?
