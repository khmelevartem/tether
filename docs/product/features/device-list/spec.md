# Device list screen

**Area:** UI
**Status:** `in progress`
**GitHub Issues:** [#7](https://github.com/khmelevartem/tether/issues/7) — Android + iOS done; Desktop tbd

---

## Why

mDNS discovery already works on every platform. What is missing everywhere is a way for the user to see the result. Without a visible list, none of the rest of Tether is reachable: the user cannot pick a target, so cannot send a file. This screen turns "Tether works" from a developer claim into something a real user can experience — on all platforms, looking the same on purpose.

## What it does

When the user opens Tether, they see a list of devices currently on their Wi-Fi that also run Tether, plus devices the user has already paired with — even when those paired devices are not currently reachable. The list is live: reachable devices appear as Tether finds them and disappear when they leave the network; paired-but-offline devices stay visible so the list is meaningful between sessions. While nothing has been found yet *and* the user has no paired devices, the screen explains that it is searching, so the user does not assume the app is broken or stuck.

Each row identifies one device clearly enough to pick the right one. Tapping a row is the entry point to sending a file — the picker and the transfer flow live in another feature; here it leads to the next step.

When the local device itself has no usable network, the entire list is replaced by the "no local network" state owned by [wifi-availability/spec.md](../system/wifi-availability/spec.md).

## Row contract

Every row on the device list is one of four cases, by reachability × pairing:

- **Online & unpaired** — standard row. Tappable, leads to file-send.
- **Online & paired** — standard row with the peer-identity accent (the warm copper/amber hue Tether uses for peer identity elsewhere, see [design.md](../../design.md)). Tappable, leads to file-send.
- **Offline & paired** — dimmed standard row with the same peer-identity accent. Tapping surfaces an inline hint ("Not on this network. Make sure Wi-Fi is on and Tether is running on it."), not a transfer flow.

  > **Extended by [clipboard transfer](../clipboard-transfer/ux-brief.md#owned-extension-the-offline-paired-row-becomes-expandable-while-it-holds-clipboard-items):** while an offline-paired row holds pending received clipboard items, it becomes expandable — gaining an expand chevron and unread badge — so a received clipboard item stays reachable regardless of whether its sender is currently online. With no pending items the row keeps the hint-only behaviour above.

- **Offline & unpaired** — not shown.

Within the list, reachable peers come first; offline-paired rows are sorted below them, **last-seen first** (most recently online → top of the offline section). A paired device coming online transitions in place from the offline row to the online row — no insertion/removal, no position jump. Going offline is the same in reverse.

The exact dimming, accent placement, and row geometry are owned by the [UX brief](ux-brief.md); this spec fixes only which cases appear and what signal each carries.

## User flows

**Primary flow**

1. User opens Tether.
2. The screen shows "Searching for devices…" with a progress indicator.
3. Within a few seconds, devices on the same Wi-Fi appear as cards in the list.
4. The user taps a device → goes to the next step (file send, owned by another feature).

**Alternative paths**

- **No reachable peers, no paired devices either.** The "Searching…" state stays visible. Nothing is shown as an error — a quiet network is not a failure.
- **No reachable peers, but paired devices exist.** The list shows the paired devices as offline rows (see [Row contract](#row-contract)). No "Searching…" overlay.
- **Reachable peer leaves the network.** If unpaired — its card disappears. If paired — the row transitions to the offline state and stays in the list.
- **Paired-but-offline device comes back.** The row transitions in place from offline to online within a few seconds. No user action required, no pull-to-refresh.
- **User taps an offline-paired row.** Inline hint appears explaining what to check on the other device. Tap elsewhere to dismiss. Does not enter file-send.
- **Local device loses the network.** The list is replaced by the "no local network" state — see [wifi-availability/spec.md](../system/wifi-availability/spec.md).
- **User changes screen orientation / window size.** The list and the search state survive; nothing flickers and discovery does not start over.
- **Two devices share a display name.** Both are shown; the row carries enough additional information to tell them apart.

## What "working" looks like

- Opening the app shows a "Searching…" state immediately, not a blank screen.
- A peer running Tether on the same Wi-Fi shows up in the list within roughly five seconds.
- Turning that peer off makes its card disappear within a few seconds.
- A paired device the user knows about appears in the list as an offline row even when not currently reachable, clearly visually distinct from reachable peers.
- A paired device coming back online stops being shown as offline within roughly five seconds; the same row goes from dimmed to active without jumping.
- A one-minute idle session on the screen produces no crashes, no freezes, and no error toasts.
- Tapping a reachable row leads to the next step (file-send placeholder is acceptable while the transfer feature is unbuilt; a dead tap is not). Tapping an offline-paired row surfaces the inline hint and does NOT enter the send flow.

## Platform notes

- **iOS / macOS:** the system asks for Local Network access on first mDNS browse (when the device list is entered) — until the user grants it, the list stays in the "Searching…" state. The exact prompt timing per platform/version and the wording of the OS prompt are owned by the [permissions strategy](../system/permissions/spec.md) feature.
- **Android:** rotation must not blank the list or restart discovery (state survives `Activity` recreation).
- **Desktop:** window resize must not reset state.

## Not in this feature

- Picking a file, sending it, and showing transfer progress — separate feature.
- Showing incoming transfers or notifications.
- Pairing prompts and trust UI — owned by the pairing features.
- Network-state detection and the no-local-network screen — owned by [wifi-availability/spec.md](../system/wifi-availability/spec.md). This spec only commits that when the local network is missing, the device-list area is taken over by that state.
- "Forget device" / paired-devices management surface — covered by a separate pairing-management feature, not by this list.
- "Ping" / "wake" action on offline-paired rows — out of scope. The row is the natural extension point if a wake mechanism is ever added.
- Custom visual design or theming. Default Material look is enough.

## Open product questions

- Sorting order of reachable peers. Discovery order is the default proposal; alternatives (alphabetical, last-used-first) become interesting once there are enough peers to scroll. Offline-paired group is already settled as last-seen first.
- What identifying detail beyond the device name belongs on each row, if anything. Two laptops named "MacBook" should be distinguishable, but the right detail is not obviously the IP for a non-technical user.
- Final copy of the offline-paired row hint — locked in the [UX brief](ux-brief.md).
