# Wi-Fi availability

**Area:** System integration
**Status:** `scoped`
**GitHub Issues:** _tbd_

---

## Why

Tether is local-first: discovery, pairing and transfer all assume the device is on a local network where peers can reach each other directly. When that network is entirely missing, none of the discovery paths Tether uses ([hotspot-transfer/spec.md](../../hotspot-transfer/spec.md) describes the layered story) can work.

Without a dedicated state for that case, the device list shows "Searching for devices…" with nothing actually being searched — the user is left guessing whether Tether is broken, the room is empty, or the network is just slow. Tether's job is to name the cause: "your network is off — turn it on." One quick toggle in the OS shade and Tether comes back to life.

The same screen also surfaces paired devices the user already knows about but that are not currently reachable, so the device list keeps useful context even when discovery returns nothing.

## What it does

When Tether opens the device list, it knows whether the user is on a local network capable of carrying Tether traffic. If not — Wi-Fi is off on a phone, no network at all on a desktop — the screen shows a clear "no local network" state instead of pretending to search. The state names the cause in one short line and points the user to the OS setting that fixes it.

The user does not have to restart the app, refresh, or do anything except turn the network back on. As soon as the device is back on a usable network, the device list starts populating again on its own.

A network the device is *sharing* — its own personal hotspot acting as the access point for others — counts as the same active state as a network the device is *joining* from outside. The "no local network" state is shown only when the device is on no usable network at all (neither joined nor shared). The hotspot case is the user expectation Tether has to meet, not a degraded mode (see [hotspot-transfer/spec.md](../../hotspot-transfer/spec.md) for the discovery story).

Devices the user has already interacted with stay visible in the list even when they are not reachable right now — the same list, with the same row identity, the user would see if those devices were online. This is one concept: previously-paired devices the user is likely to send to again, surfaced wherever the user picks a peer. Once the local device is on a usable network, the list always carries either live peers, or known peers shown as offline, or both — there is no "blank and unexplained" list to stare at. When the local device itself has no usable network, the screen instead shows the "no local network" state and the device list (including any offline paired rows) is replaced by it: nothing actionable can be shown until the local network is back.

## Row contract

Every row on the device list is one of four cases, by reachability × pairing:

- **Online & unpaired** — standard row.
- **Online & paired** — standard row with the peer-identity accent (the warm copper/amber hue Tether uses for peer identity elsewhere, see [design.md](../../../design.md)).
- **Offline & paired** — dimmed standard row with the same peer-identity accent.
- **Offline & unpaired** — not shown.

The exact dimming, accent placement, and row geometry are owned by the [UX brief](ux-brief.md); this spec fixes only which cases appear and what signal each carries.

## User flows

**Primary flow — Wi-Fi off when opening the app**

1. User opens Tether on a phone with Wi-Fi turned off.
2. The device list shows the "Wi-Fi is off" state immediately: short title, one-line rationale, and — on platforms where the OS exposes a one-tap path to the Wi-Fi toggle — an "Open Wi-Fi settings" action. See [Platform notes](#platform-notes) for which platforms qualify.
3. User reaches the OS Wi-Fi controls (via the in-app action where present, or via the system menu / shade where not). User turns Wi-Fi on, returns to Tether.
4. Within a few seconds, the device list switches to the searching state and peers begin to appear.

**Recovery mid-session**

1. User has the device list open. Wi-Fi drops (toggled off, lost signal, airplane mode).
2. Within a few seconds the screen switches to the "no local network" state, without restart.
3. When Wi-Fi returns, the device list switches back to searching, and known peers reappear within a few seconds.

**Desktop without Wi-Fi (e.g. ethernet-only tower)**

1. User opens Tether on a desktop on Ethernet only.
2. As long as that wired network carries Tether traffic to other devices, the screen behaves as a normal local network — searching state, peers appear normally.
3. If the desktop is on no usable network at all, the "no local network" state is shown — with neutral wording ("You're not on a local network") rather than mentioning Wi-Fi specifically.

**Paired device that is currently offline (local device on a network)**

1. User has previously paired with another device. The local device is on a usable local network. The paired device is currently powered off, on a different network, or simply not running Tether.
2. The device list shows that paired device as an offline row, distinct from currently-reachable devices, with a short hint: "Not on this network. Make sure Wi-Fi is on and Tether is running on it."
3. The row is not tappable for sending — tapping it surfaces the same hint, not a transfer flow.
4. When the paired device comes back online, its row transitions to the normal reachable state without the user having to refresh.

If the local device itself has no usable network, the "no local network" state takes over the screen and the offline rows are not shown — the user's own network is the first thing to fix, and showing offline-peer hints in that situation would be misleading.

## What "working" looks like

- Opening the app on a device with Wi-Fi off shows the "Wi-Fi is off" state immediately, not a "Searching…" spinner.
- The state explains the cause in plain language and offers an obvious next step (the OS setting), not a generic "no connection" toast.
- Turning Wi-Fi on while the screen is open causes the list to begin populating within roughly five seconds, with no restart, no manual refresh, and no error along the way.
- Turning Wi-Fi off while the screen is open switches the list to the "no local network" state within roughly five seconds.
- A desktop on Ethernet that can reach other Tether devices behaves exactly like a desktop on Wi-Fi — no wording about Wi-Fi appears.
- A paired device the user knows about appears in the list even when it is not currently reachable, marked clearly as offline, with a hint about what the user can check on the other side — provided the local device itself is on a usable network.
- A device that has just come back online stops being shown as offline within roughly five seconds and behaves like any other reachable peer.
- A device sharing its own hotspot sees the same device list, the same searching state, and the same peers as a device that joined a hotspot from outside — there is no separate "I'm the host" mode.
- The visual presentation of every state — searching, peers, paired-offline, no-local-network — is the same across Android, iOS, macOS and Desktop.

## Platform notes

- **Visual language.** The searching and "no network" states use Tether's shared visual language — see [design.md](../../../design.md). Searching reuses the existing animated brand-mark indicator the rest of the app uses for "waiting for a peer". The "no network" state must be visually distinct from searching so that the user can tell at a glance that the situation is different — exact visual treatment is deferred to implementation.
- **Wording per platform shape.** On a phone, where Wi-Fi is the only realistic local-network surface, the title is "Wi-Fi is off" with a one-line rationale. On a desktop where Ethernet is a normal substitute, the title is the more neutral "You're not on a local network". Exact copy and per-platform deltas live in the [UX brief](ux-brief.md).
- **Action availability.** The "Open Wi-Fi settings" button is shown only on platforms where the OS exposes a one-tap path that lands the user directly on the Wi-Fi toggle. On platforms without such a direct path, the state shows a one-line written instruction instead. Per-platform table in the [UX brief](ux-brief.md).
- **Same behaviour on every platform.** Detection of network state is a single product contract — every platform reports the same thing: "the user has, or does not have, a local network capable of carrying Tether traffic". The UI never branches on which platform reported it.

## Not in this feature

- Wi-Fi credentials, network selection, captive portals, "join this network" UI — Tether does not manage networks, only reacts to their state.
- Cellular / mobile-data fallback — out by design, see [vision.md](../../../vision.md): "If the LAN can't carry it, we say so honestly."
- Internet reachability — Tether is local; whether the LAN reaches the internet is irrelevant. A captive-portal Wi-Fi is treated as a normal network.
- VPN as a discovery surface — when a VPN is active over Wi-Fi, Tether treats the underlying Wi-Fi as the network and does not try to discover peers across the VPN tunnel. The user does not see anything VPN-specific.
- Tethering / phone hotspot as a Tether transport — supported, but owned by [hotspot-transfer/spec.md](../../hotspot-transfer/spec.md). Wi-Fi availability still treats the host's own hotspot interface as "the local network is present"; whether discovery succeeds across that interface is the other spec's concern.
- Wi-Fi drop during an in-progress transfer — that is a transfer-failure case and lives in [file-transfer](../../file-transfer/spec.md), not here.
- Surfacing the "no local network" state on screens other than the device list (e.g. as a banner on pairing or pre-flight transfer screens) — the device list is the single surface for this state. The user cannot reach the pairing or transfer screens without first picking a peer in the device list, so a missing network always shows up there first.
- "Same Wi-Fi but different SSIDs / VLANs / AP isolation / multicast-blocked guest networks" — when two peers cannot reach each other despite both having a network, neither side has a local-network problem in the sense of this spec. The user's escape hatch (QR scan, manual IP entry, recent peers) is owned by [hotspot-transfer/spec.md](../../hotspot-transfer/spec.md); the empty-but-searching state itself lives in [device-list](../../device-list/spec.md).
- Permission prompts (iOS / macOS Local Network, Android Local Network) — separate feature, see [permissions/spec.md](../permissions/spec.md). Wi-Fi availability assumes any required permission is already granted; if permission is missing, that is a permissions empty-state, not a "no network" empty-state.
- The "Forget device" / paired-devices management surface — out of scope; only the *display* of currently-offline paired devices in the list is in scope here.
- Implementation of how the trusted-device list is delivered to the device-list screen — that is an implementation question for the feature issue, not a product decision.

## Open product questions

- Final copy of the rationale line and the offline-row hint — locked in the [UX brief](ux-brief.md).
- Visual realisation of the four-case row contract (dimming amount, accent placement, row geometry) — also locked in the [UX brief](ux-brief.md); the contract itself is fixed in [Row contract](#row-contract) above.
- Whether tapping an offline paired row should offer a "ping" / "wake up" action. Out for MVP (Tether has no wake mechanism); the row is the natural place for it later.
