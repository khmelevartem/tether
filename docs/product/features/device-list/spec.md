# Device list screen

**Area:** UI
**Status:** `in progress`
**GitHub Issues:** [#7](https://github.com/khmelevartem/tether/issues/7) — Android + iOS done; Desktop tbd

---

## Why

mDNS discovery already works on every platform. What is missing everywhere is a way for the user to see the result. Without a visible list, none of the rest of Tether is reachable: the user cannot pick a target, so cannot send a file. This screen turns "Tether works" from a developer claim into something a real user can experience — on all platforms, looking the same on purpose.

## What it does

When the user opens Tether, they see a list of devices currently on their Wi-Fi that also run Tether, plus devices the user has already paired with — even when those paired devices are not currently reachable. The list is live: reachable devices appear as Tether finds them and disappear when they leave the network; paired-but-offline devices stay visible so the list is meaningful between sessions. While nothing has been found yet *and* the user has no paired devices, the screen explains that it is searching, so the user does not assume the app is broken or stuck.

The four row cases (online & unpaired / online & paired / offline & paired / offline & unpaired — not shown) and the network-absent empty-state are defined in [wifi-availability/spec.md](../system/wifi-availability/spec.md). The base live-discovery list shipped under #7 on Android and iOS; the offline-paired rows, the four-case row contract, and the no-local-network state are pending the wifi-availability implementation issue and not yet shipped on any platform.

Each row identifies one device clearly enough to pick the right one. Tapping a row is the entry point to sending a file — the picker and the transfer flow live in another feature; here it leads to the next step.

## User flows

**Primary flow**

1. User opens Tether.
2. The screen shows "Searching for devices…" with a progress indicator.
3. Within a few seconds, devices on the same Wi-Fi appear as cards in the list.
4. The user taps a device → goes to the next step (file send, owned by another feature).

**Alternative paths**

- **No reachable peers, no paired devices either.** The "Searching…" state stays visible. Nothing is shown as an error — a quiet network is not a failure.
- **No reachable peers, but paired devices exist.** The list shows the paired devices as offline rows — see [wifi-availability/spec.md](../system/wifi-availability/spec.md) for the row contract and hint copy. No "Searching…" overlay.
- **Reachable peer leaves the network.** If unpaired — its card disappears. If paired — the row transitions to the offline state and stays in the list.
- **Local device loses the network.** The list is replaced by the "no local network" state — see [wifi-availability/spec.md](../system/wifi-availability/spec.md).
- **User changes screen orientation / window size.** The list and the search state survive; nothing flickers and discovery does not start over.
- **Two devices share a display name.** Both are shown; the row carries enough additional information to tell them apart.

## What "working" looks like

- Opening the app shows a "Searching…" state immediately, not a blank screen.
- A peer running Tether on the same Wi-Fi shows up in the list within roughly five seconds.
- Turning that peer off makes its card disappear within a few seconds.
- A one-minute idle session on the screen produces no crashes, no freezes, and no error toasts.
- Tapping a device leads to the next step — a placeholder is acceptable while the transfer feature is not yet implemented; a dead tap is not.

## Platform notes

- **iOS / macOS:** the system asks for Local Network access on first mDNS browse (when the device list is entered) — until the user grants it, the list stays in the "Searching…" state. The exact prompt timing per platform/version and the wording of the OS prompt are owned by the [permissions strategy](../system/permissions/spec.md) feature.
- **Android:** rotation must not blank the list or restart discovery (state survives `Activity` recreation).
- **Desktop:** window resize must not reset state.

## Not in this feature

- Picking a file, sending it, and showing transfer progress — separate feature.
- Showing incoming transfers or notifications.
- Pairing prompts and trust UI — owned by the pairing features.
- Custom visual design or theming. Default Material look is enough.

## Open product questions

- Sorting order in the list. Discovery order is the default proposal; alternatives (alphabetical, last-used-first) become interesting once there are enough peers to scroll.
- What identifying detail beyond the device name belongs on each row, if anything. Two laptops named "MacBook" should be distinguishable, but the right detail is not obviously the IP for a non-technical user.
