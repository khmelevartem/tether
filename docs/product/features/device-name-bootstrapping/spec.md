# Device name bootstrapping

**Area:** Onboarding / Identity
**Status:** `scoped`
**GitHub Issues:** _tbd_

---

## Why

Every Tether device announces a human-readable name over mDNS. That name is what neighbours see in the [device list](../device-list/spec.md) and what the user picks when sending a file. The developer-only CLI runner already exposes the name as a launch argument; on Android / iOS / macOS / Desktop UI there is no defined default and no defined way for the user to see what their own device is calling itself.

If nothing is decided, every Android phone shows up as a bare device model ("Pixel 7", "SM-S908B") and every desktop as a technical hostname ("artem-mbp.lan"). Two devices of the same owner become indistinguishable, which breaks Tether's primary use case — transferring between *one's own* devices.

The vision commits to *"settable device name (implicit in pairing/discovery; full settings screen comes in Post-MVP)"*. This feature delivers half of that commitment now: each platform announces a sensible default and the user can see, on the device list, what their own device is currently called. The "settable" half — the user actually editing the name — is a follow-up that this spec scopes but does not implement.

## What it does

On every launch, Tether computes a default name from the OS — the most personal one available without extra permissions, falling back to the device model. That name is what mDNS announces and what every peer sees in their device list. The user does not type anything during onboarding; there is no naming step.

Inside the app, the device list shows a small surface that tells the user what their own device is called ("This device: Pixel 7"). In MVP this surface is read-only. The same surface is the seat for a future inline rename affordance (pencil icon / tappable area) — keeping the rename close to the name, so a separate Settings screen may not be needed at all.

The name is not a unique identifier. Two devices on the same network can legitimately end up with identical user-visible names; making them distinguishable is the device list's job (secondary detail on the row), not the name bootstrap's.

## User flows

**Primary flow — first launch**

1. User installs Tether and opens it.
2. Tether computes the platform default from the OS and starts announcing it over mDNS.
3. The device list opens. A surface on the screen shows "This device: <default name>".
4. The user proceeds to discover or send to peers. No naming step blocks them.

**Alternative paths**

- **Subsequent launches.** Tether re-derives the default from the OS source on every launch — there is no persisted user-chosen name yet. If the user has changed their device's OS-level name (e.g. macOS Computer Name), Tether picks up the new value on the next launch.
- **Reinstall.** Identical to a first launch — the OS source has not changed, so the announced name is the same.
- **OS source unavailable / returns empty.** Tether uses the per-platform fallback (see Platform notes). The surface still shows a non-empty name.
- **Two devices announce the same user-visible name.** Both still appear in every peer's device list. Tether does not auto-suffix the user-visible name; the device list row carries an extra detail (see [device-list spec](../device-list/spec.md) → "Two devices share a display name") to tell them apart. At the discovery layer, the mDNS service instance name already carries a per-instance identifier so peers are never confused, even when their user-visible names collide.
- **"This device" surface fails to render.** Discovery and transfer still work; peers still see the name. The surface shows a degraded state ("naming surface is currently unavailable") rather than blocking the rest of the screen.

## What "working" looks like

- A fresh install on every supported platform announces a non-empty default name and never falls through to a raw technical string — internal build aliases, loopback names, or firmware-level hardware identifiers are all unacceptable.
- The device list shows the user what their own device is currently called, within a second of opening the screen.
- Two devices of the same owner with different OS-level names show up under those different names in every peer's device list. Two devices of the same owner with the same OS-level name (e.g. two identical Pixel 7) show up as duplicates — that case is handled by the device list, not here.
- Restarting the app does not change the announced name unless the user changed it at the OS level.

## Platform notes

User-visible default per platform — Tether takes the most personal name the OS will give without extra permissions, and falls back to the device model or owner otherwise:

- **Android:** the device model as the system reports it ("Pixel 7", "SM-S908B"). Android does not expose an OS-level owner name without account permissions, so the model is the honest default.
- **iOS:** the system "device name" that iOS provides to apps without additional permissions. On recent iOS versions this is typically a generic "iPhone" unless the user has personalised it; Tether accepts whatever the OS returns.
- **macOS:** the OS "Computer Name" (the one shown in System Settings → General → About, e.g. "Artem's MacBook Pro"). This is typically already personalised by the macOS setup assistant.
- **Desktop (JVM):** the OS hostname. If it is empty, a loopback alias, or another technical placeholder, fall back to a name derived from the OS account user name ("Artem's Desktop").

## Not in this feature

- **User-initiated rename.** The "This device" surface is read-only here; an editable affordance on the same surface is a follow-up feature.
- **Local alias for a paired peer.** A user may want to display a peer under a different name in their own device list (e.g. "Mum's laptop" for a peer that calls itself "Computer"). This is a per-device-list override that does not affect the peer or its mDNS announcement. Owned by the [device list](../device-list/spec.md) / [pairing](../pairing/spec.md) features.
- **A full Settings screen** for renaming.
- **Auto-suffixing user-visible names** ("Pixel 7 (2)") to make them unique. The mDNS service instance already includes a per-instance identifier so the discovery layer never confuses two same-named peers; user-visible names are deliberately kept free of machine-generated suffixes.
- **Distinguishing two peers with identical user-visible names on the device list** — that detail is the device list's concern.
- **Syncing the name across the user's devices** (cloud, account) — Tether has no account.
- **Profanity filtering or character whitelists** for the future user-typed name.

## Open product questions

- The exact form of the "This device" surface on the device list — banner along the top, header inside the list, separate strip, or something else. Deferred to the ux-expert phase that owns the device list visual design.
- The exact form of the future inline rename affordance on that surface (pencil icon, tappable underline, separate dialog with editable field). Deferred to the ux-expert phase together with the read-only form.
