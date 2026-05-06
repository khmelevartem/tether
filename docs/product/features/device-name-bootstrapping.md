# Device name bootstrapping

**Area:** Onboarding / Identity
**Status:** `idea`
**GitHub Issues:** _tbd_

---

> Stub. Captures the gap. Flesh out before the feature enters a sprint.

## Why

The vision says "settable device name (implicit in pairing/discovery; full settings screen comes in Post-MVP)". On the CLI this is `--name`. On Android / iOS / macOS / Desktop UI there is currently no thought about what name a fresh install advertises, or whether the user can change it without a Settings screen (which is Post-MVP).

If we get this wrong, every Android phone shows up as "Pixel 7" or worse "android-build" — indistinguishable when the user owns two.

## What it does (sketch)

- On first launch each platform picks a sensible default name (hostname / device model / user-friendly variant).
- The user can edit it once on first launch — a single inline rename, not a full settings screen.
- The chosen name is what appears on every other peer's device list.

## Open product questions

- Default source per platform — `Build.MODEL` on Android, `UIDevice.name` on iOS, `hostname` on desktop?
- First-launch rename — full screen, banner with edit affordance, or skipped entirely until Post-MVP settings?
