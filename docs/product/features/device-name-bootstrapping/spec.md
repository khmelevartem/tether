# Device name bootstrapping

**Area:** Onboarding / Identity
**Status:** `scoped`
**GitHub Issues:** _tbd_

---

## Why

Every Tether device announces a human-readable name over mDNS. That name is what neighbours see in the [device list](../device-list/spec.md) and what the user picks when sending a file. The developer-only CLI runner already lets the operator set the name at startup; on Android / iOS / macOS / Desktop UI there is no defined default and no way for the user to see, or change, what their own device is calling itself.

Without a way to override it, the OS-derived default leaves every Android phone showing up as a bare device model ("Pixel 7", "SM-S908B") and every desktop as a technical hostname ("hostname.local"). Two devices of the same owner become indistinguishable, which breaks Tether's primary use case — transferring between *one's own* devices. A read-only default is therefore not enough; the user has to be able to fix the name when the OS-derived value is not good enough.

The vision commits to *"settable device name (implicit in pairing/discovery; full settings screen comes in Post-MVP)"*. This feature delivers that commitment: each platform announces a sensible default, the device list shows the user what their own device is currently called, and the same surface lets them rename it at any time. No separate Settings screen is needed.

## What it does

On first launch each platform computes a default name from the OS — the most personal one available without extra permissions, falling back to the device model. That name is what mDNS announces and what every peer sees in their device list, immediately, with no naming step blocking onboarding.

Inside the app, the device list shows a small surface that tells the user what their own device is called ("This device: Pixel 7") and lets them edit it inline. Tapping the affordance reveals an editable field; saving a non-empty name (up to 50 characters) persists the new name and updates the mDNS announcement so every peer sees the change. The same surface is the rename surface — there is no separate Settings screen.

A user-chosen name is persisted locally and survives app restart. The OS-derived default is only used when there is no user-chosen name on file. Pairing is keyed by a stable device identity, not by display name, so renaming a device does not break existing pairs.

The name is not a unique identifier. Two devices on the same network can legitimately end up with identical user-visible names; making them distinguishable on a peer's screen is the device list's job (secondary detail on the row), not the name bootstrap's.

## User flows

**Primary flow — first launch**

1. User installs Tether and opens it.
2. Tether computes the platform default from the OS and starts announcing it over mDNS.
3. The device list opens. A surface on the screen shows "This device: <default name>" with an edit affordance next to it.
4. The user proceeds to discover or send to peers. No naming step blocks them.

**Renaming the device**

1. User taps the edit affordance on the "This device" surface.
2. The name becomes editable, pre-filled with the current name.
3. User types a new name (non-empty, up to 50 characters) and confirms.
4. Tether persists the new name and republishes the mDNS announcement. Publishing the new name requires tearing down and re-establishing the local discovery session, so the user's own device list may briefly clear and repopulate.
5. Within a few seconds every peer that has Tether open sees the new name in its device list.

**Alternative paths**

- **Subsequent launches with no user-chosen name.** Tether re-derives the default from the OS source on every launch. If the user has changed their device's OS-level name (e.g. macOS Computer Name), Tether picks up the new value.
- **Subsequent launches with a user-chosen name.** The stored name is used, regardless of any change in the OS source.
- **Reinstall.** Persisted name is gone; the device returns to the OS-derived default on the next launch.
- **OS source unavailable / returns empty on first launch.** Tether uses the per-platform fallback (see Platform notes). The surface still shows a non-empty name.
- **Invalid name on rename.** Empty / whitespace-only input or input longer than 50 characters is rejected inline (save disabled or clear inline error); the previous name stays in effect.
- **Storage write fails on rename.** The surface shows an inline error ("could not save"); the previous name stays in effect; nothing is re-announced.
- **Republishing the mDNS announcement fails on rename.** The new name is persisted locally and visible on the user's own surface immediately. The previous announcement may still be live to peers until republishing succeeds; the surface shows an inline "could not announce — try again" affordance, and the user can re-save to retry.
- **The "This device" surface fails to render.** Discovery and transfer still work; peers still see the device. The surface degrades to a placeholder ("naming is currently unavailable") rather than blocking the rest of the screen.
- **Two devices announce the same user-visible name.** Tether does not auto-suffix the user-visible name. How peers handle the collision on a single list — keeping both entries, adding a secondary detail, or otherwise — is the device list's concern (see [device-list spec](../device-list/spec.md) → "Two devices share a display name"). This feature does not own that resolution.
- **Rename during an in-flight transfer.** The transfer continues to completion; the name change only affects the mDNS announcement and what peers see in their device list, not active connections.

## What "working" looks like

- A fresh install on every supported platform announces a non-empty default name and never falls through to a raw technical string — internal build aliases, loopback names, or firmware-level hardware identifiers are all unacceptable.
- The device list shows the user what their own device is currently called, within a second of opening the screen.
- Tapping the edit affordance, typing a new name, and confirming changes the announced name. Every peer that has Tether open sees the new name in its device list within a few seconds.
- Closing and reopening the app preserves the user-chosen name.
- Reinstalling Tether returns the device to the OS-derived default.
- Two devices of the same owner that start with the same default (e.g. two identical Pixel 7) can be told apart after one rename.
- A device that has already been paired with another stays paired across renames on either side. The pair does not need to be re-established.

## Platform notes

User-visible default per platform — Tether takes the most personal name the OS will give without extra permissions, and falls back to the device model or owner otherwise:

- **Android:** the device model as the system reports it ("Pixel 7", "SM-S908B"). Android does not expose an OS-level owner name without account permissions, so the model is the honest default.
- **iOS:** the system "device name" that iOS provides to apps without additional permissions. On recent iOS versions this is typically a generic "iPhone" unless the user has personalised it; Tether accepts whatever the OS returns.
- **macOS:** the OS "Computer Name" (the one shown in System Settings → General → About, e.g. "Artem's MacBook Pro"). This is typically already personalised by the macOS setup assistant.
- **Desktop:** the OS hostname. If it is empty, a loopback alias, or another technical placeholder, fall back to a name based on the account the device is logged in under ("Artem's Desktop").

## Not in this feature

- **A separate Settings screen** for renaming.
- **Local alias for a paired peer.** A user may want to display a peer under a different name in their own device list (e.g. "Mum's laptop" for a peer that calls itself "Computer"). This is a per-device-list override that does not affect the peer or its mDNS announcement, and is owned by the [device list](../device-list/spec.md) / [pairing](../pairing/spec.md) features.
- **Auto-suffixing user-visible names** ("Pixel 7 (2)") to make them unique. User-visible names are deliberately kept free of machine-generated suffixes; handling same-named peers in the list is the device list's responsibility.
- **Distinguishing two peers with identical user-visible names on the device list** — that detail is the device list's concern.
- **Syncing the name across the user's devices** (cloud, account) — Tether has no account.
- **Profanity filtering or character restrictions** beyond basic length validation.

## Open product questions

- The exact form of the "This device" surface on the device list — banner along the top, header inside the list, separate strip, or something else. Deferred to the ux-expert phase that owns the device list visual design.
- The exact form of the edit affordance on that surface (pencil icon, dotted-underlined tappable area, separate dialog with editable field). Deferred to the same ux-expert phase.
