# Device name bootstrapping

**Area:** Onboarding / Identity
**Status:** `scoped`
**GitHub Issues:** _tbd_

---

## Why

Every Tether device announces a human-readable name over mDNS. That name is what neighbours see in the [device list](../device-list/spec.md) and what the user picks when sending a file. Until this feature, only the developer CLI runner had a way to set it; on Android / iOS / macOS / Desktop UI there is no defined default and no way to change it (a full Settings screen is Post-MVP — see [vision.md](../../vision.md)).

If nothing is decided, every Android phone shows up as a bare device model ("Pixel 7", "SM-S908B") and every desktop as a technical hostname ("artem-mbp.lan"). Two devices of the same owner become indistinguishable, which breaks Tether's primary use case — transferring between *one's own* devices.

The vision commits to *"settable device name (implicit in pairing/discovery; full settings screen comes in Post-MVP)"*. This feature delivers that commitment for the MVP: a sensible default on every platform plus exactly one chance, on first launch, to change it.

## What it does

On first launch each platform proposes a name it can derive locally — the most personal one the OS will give without extra permissions, falling back to the device model. The user sees the proposed name on the welcome step, can edit it inline, and confirms. From that point on the chosen name is what Tether announces in mDNS and what every peer sees in their device list.

Subsequent launches use the stored name silently — no prompt, no banner. The user cannot change the name again until the Post-MVP Settings screen exists; this is an explicit MVP trade-off, not a bug.

The name is not a unique identifier. Two devices in the same network may legitimately end up with identical user-visible names; making them distinguishable is the device list's job (secondary detail on the row), not the name bootstrap's.

## User flows

**Primary flow — first launch with default**

1. User installs Tether and opens it.
2. After the welcome step, Tether shows a "Name this device" screen with the platform default already filled in (e.g. "Pixel 7", "Артём's iPhone", "artem-mbp", "Artem's MacBook").
3. User taps "Continue".
4. Tether stores the name and proceeds to the device list. From this moment the name is what every peer sees over mDNS.

**Alternative paths**

- **First launch with rename.** User edits the proposed name on the bootstrap screen ("Pixel 7" → "Phone"), taps "Continue". Stored name is the edited one.
- **Empty / whitespace name on bootstrap.** "Continue" is disabled or silently rejected; the user must leave a non-empty name. Tether does not fall back to default silently — the user is on this screen exactly to make a choice.
- **Subsequent launch.** Welcome and naming steps are skipped; Tether opens straight to the device list announcing the stored name.
- **Reinstall.** Storage is gone; the next launch is treated as a first launch — user sees the bootstrap screen again with a freshly derived default.
- **Storage write failure on bootstrap.** Tether shows a generic "could not save settings" error and lets the user retry. The device does not enter the device list until a name is persisted; otherwise the next launch would silently re-prompt and the user would assume the first attempt was lost.
- **Two devices announce the same user-visible name.** Both still appear in every peer's device list. Tether does not auto-suffix the user-visible name; the device list row carries an extra detail (see [device-list spec](../device-list/spec.md) → "Two devices share a display name") to tell them apart. At the discovery layer, the mDNS service instance name already carries a per-instance identifier so peers are never confused, even when their user-visible names collide.

## What "working" looks like

- A fresh install on every supported platform proposes a default name that is **not** empty and not a raw technical string (no `android-build`, no `localhost`, no `iPhone15,3`).
- The bootstrap screen appears exactly once — on the first launch — and never again until reinstall.
- The name the user confirmed (default or edited) is what peers see in their device list within a few seconds.
- Closing and reopening the app does not show the bootstrap screen and does not change the announced name.
- Reinstalling Tether brings the bootstrap screen back with a freshly derived default.
- Two different devices of the same owner can be given different names by the user and are then clearly distinguishable in every peer's device list.

## Platform notes

User-visible default per platform — Tether takes the most personal name the OS will give without extra permissions, and falls back to the device model otherwise:

- **Android:** the device model as the system reports it ("Pixel 7", "SM-S908B"). Android does not expose an OS-level owner name without account permissions, so the model is the honest default. Owners of two same-model phones will see them as duplicates until they rename on bootstrap.
- **iOS:** the system "device name" that iOS provides to apps. On recent iOS versions this is typically a generic "iPhone" unless the user has personalised it; we accept whatever the OS hands us and rely on the user to rename if desired. Tether does **not** request the special entitlement that returns a personalised name for MVP.
- **macOS:** the OS "Computer Name" (the one shown in System Settings → General → About, e.g. "Artem's MacBook Pro"). This is typically already personalised by the macOS setup assistant.
- **Desktop (JVM):** the OS hostname. If it is empty, a loopback alias, or another technical placeholder, fall back to a name derived from the OS account user name ("Artem's Desktop").

## Not in this feature

- A full Settings screen with a permanent rename affordance — Post-MVP.
- Renaming the device on any launch after the first one — explicitly out of scope until Settings.
- Distinguishing two peers with identical user-visible names on the device list — that detail (IP last octet, device kind icon, etc.) is the device list's concern, not the name bootstrap's.
- Auto-suffixing user-visible names ("Pixel 7 (2)") to make them unique. The mDNS service instance already includes a per-instance identifier so the discovery layer never confuses two same-named peers; we deliberately keep user-visible names free of machine-generated suffixes.
- Syncing the name across the user's devices (cloud, account) — Tether has no account.
- Validating the name against profanity, length limits beyond what mDNS itself enforces, or character whitelists — we accept whatever the user types up to mDNS's own constraints.

## Open product questions

- Whether the bootstrap step should be its own screen or a field on the existing welcome screen. UX call deferred to implementation; either is acceptable as long as it appears exactly once on first launch and the user cannot proceed without a non-empty name.
- Maximum length and character set for the user-edited name beyond mDNS's own limits. Deferred — start with mDNS limits and only tighten if real-world feedback demands it.
- Whether to offer a "skip and use default" affordance separate from "Continue" with the default text pre-filled. Current proposal: a single "Continue" button — pre-filled text *is* the skip path.
