# Permissions

**Area:** System integration
**Status:** `idea`
**GitHub Issues:** _tbd_

---

> Stub. Captures the gap. Flesh out before the feature enters a sprint.

## Why

Each platform has its own permission surface — and currently each issue (#7, #8, #35) waves at it differently. Without a single source of truth the app will either ask for too much up front (scaring users away) or fail mid-flow with cryptic errors.

Permissions involved:

- **Android** — Local Network discovery (multicast), file read (`READ_MEDIA_*` on API 33+).
- **iOS** — Local Network usage description.
- **macOS** — Local Network access on Sonoma+.

## What it does (sketch)

- Defines **when** each prompt is shown — at launch, at first relevant action, or deferred until the system itself triggers it.
- Defines **what** the user sees if a permission is denied — a graceful empty state with a way to open OS settings, not a crash and not a silent dead-end.
- Names the user-facing strings used in iOS / macOS Info.plist explanations and Android rationale dialogs.

## Not in this feature

- Wi-Fi availability — that is a system **state**, not a grant. See [wifi-availability.md](wifi-availability.md).
- Android foreground service for background receive — that is a lifecycle question, surfaces inside [file-transfer.md](../file-transfer.md).
- Desktop firewall prompts on first server bind — narrow, fixed as a Platform note in the relevant feature.

## Open product questions

- Do we ask for file read on Android up front, or only when the user picks a file? Both are common patterns; pick one and stick to it.
- iOS Local Network prompt — first launch (predictable but front-loaded) or on-demand (smoother but surprises some users).
- When a permission is denied permanently ("Don't ask again"), what does the empty state look like, and how clearly do we route the user to OS settings?
