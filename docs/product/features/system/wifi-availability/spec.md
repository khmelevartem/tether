# Wi-Fi availability

**Area:** System integration
**Status:** `scoped`
**GitHub Issues:** _tbd_

---

## Why

Tether is local-first: discovery, pairing and transfer all assume the device is on a local network — one that carries mDNS and lets peers reach each other directly by IP. When that network is missing, nothing in Tether can work.

Today the app would silently show the empty "Searching for devices…" state in that situation, which is misleading: nothing is being searched, the network is simply absent. The user is left wondering whether Tether is broken, the room is empty, or the network is just slow.

Tether's job is to make the cause obvious: "your network is off — turn it on." One quick toggle in the OS shade and Tether comes back to life. The same surface also has to honestly say something for paired devices that the user already knows about but that are not currently reachable, so the device list keeps useful context even when discovery returns nothing.

## What it does

When Tether opens the device list, it knows whether the user is on a local network capable of carrying Tether traffic. If not — Wi-Fi is off on a phone, no network at all on a desktop — the screen shows a clear "no local network" state instead of pretending to search. The state names the cause in one short line and points the user to the OS setting that fixes it.

The user does not have to restart the app, refresh, or do anything except turn the network back on. As soon as the device is back on a usable network, the device list starts populating again on its own.

Devices the user has already paired with stay visible in the list even when they are not reachable right now — shown as offline rows with a short hint about what to check on the other device. Once the local device is on a usable network, the list always carries either live peers, or known peers shown as offline, or both — there is no "blank and unexplained" list to stare at. When the local device itself has no usable network, the screen instead shows the "no local network" state and the device list (including any offline paired rows) is replaced by it: nothing actionable can be shown until the local network is back.

## User flows

**Primary flow — Wi-Fi off when opening the app**

1. User opens Tether on a phone with Wi-Fi turned off.
2. The device list shows the "Wi-Fi is off" state immediately: short title, one-line rationale, and a "Open Wi-Fi settings" action.
3. User taps the action → OS Wi-Fi settings open. User turns Wi-Fi on, returns to Tether.
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
- The visual presentation of every state — searching, peers, paired-offline, no-local-network — is the same across Android, iOS, macOS and Desktop.

## Platform notes

- **Visual language.** The searching and "no network" states use Tether's shared visual language — see [design.md](../../../design.md). Searching reuses the existing animated brand-mark indicator the rest of the app uses for "waiting for a peer". The "no network" state must be visually distinct from searching so that the user can tell at a glance that the situation is different — exact visual treatment is deferred to implementation.
- **Wording per platform shape.** On a phone, where Wi-Fi is the only realistic local-network surface, the title is "Wi-Fi is off" with a one-line rationale and an "Open Wi-Fi settings" action. On a desktop where Ethernet is a normal substitute, the title is the more neutral "You're not on a local network", with the same kind of action where the OS exposes a deep-link to network settings; otherwise a plain instruction.
- **Action availability.** The "Open settings" action is only shown where the OS provides a stable way to reach the relevant settings page directly. Where it does not, the state shows the rationale and a one-line written instruction ("Turn Wi-Fi on in the system menu") instead of an inert button.
- **Same behaviour on every platform.** Detection of network state is a single product contract — every platform reports the same thing: "the user has, or does not have, a local network capable of carrying Tether traffic". The UI never branches on which platform reported it.

## Not in this feature

- Wi-Fi credentials, network selection, captive portals, "join this network" UI — Tether does not manage networks, only reacts to their state.
- Cellular / mobile-data fallback — out by design, see [vision.md](../../../vision.md): "If the LAN can't carry it, we say so honestly."
- Internet reachability — Tether is local; whether the LAN reaches the internet is irrelevant. A captive-portal Wi-Fi is treated as a normal network.
- VPN as a discovery surface — when a VPN is active over Wi-Fi, Tether treats the underlying Wi-Fi as the network and does not try to discover peers across the VPN tunnel. The user does not see anything VPN-specific.
- Tethering / phone hotspot as a Tether transport — known limitation, not supported in this feature. Tether does not promise to work when one device hosts a personal hotspot and runs Tether on it.
- Wi-Fi drop during an in-progress transfer — that is a transfer-failure case and lives in [file-transfer](../../file-transfer/spec.md), not here.
- Surfacing the "no local network" state on screens other than the device list (e.g. as a banner on pairing or pre-flight transfer screens) — the device list is the single surface for this state. The user cannot reach the pairing or transfer screens without first picking a peer in the device list, so a missing network always shows up there first.
- "Same Wi-Fi but different SSIDs / VLANs / AP isolation" — when two peers are on physically separate networks, neither side has a local-network problem, they simply do not see each other. That is the empty-but-searching state in [device-list](../../device-list/spec.md), not a Wi-Fi-availability problem.
- Permission prompts (iOS / macOS Local Network, Android Local Network) — separate feature, see [permissions/spec.md](../permissions/spec.md). Wi-Fi availability assumes any required permission is already granted; if permission is missing, that is a permissions empty-state, not a "no network" empty-state.
- The "Forget device" / paired-devices management surface — out of scope; only the *display* of currently-offline paired devices in the list is in scope here.
- Implementation of how the trusted-device list is delivered to the device-list screen — that is an implementation question for the feature issue, not a product decision.

## Open product questions

- Exact wording of the rationale line and the offline-row hint. Working drafts are above; final copy will be settled during implementation.
- Whether the offline row shows any extra detail beyond name + "not on this network" — e.g. "last seen yesterday". For MVP probably no; revisit once we see how often paired devices are offline in real use.
- How obvious the visual distinction between "reachable but unpaired" and "paired but offline" needs to be. Both must be distinguishable from "paired and reachable", but the exact treatment (icon, opacity, badge) is a UI choice for the implementation issue.
- Whether tapping an offline paired row should offer a "ping" / "wake up" action in the future. Out for MVP — Tether has no wake mechanism — but the row is the natural place for it later.
- Behaviour on a phone hotspot when both the hosting device and a guest device run Tether. Treated as unsupported here; if user reports show this is a real expectation, reopen as a separate feature.
