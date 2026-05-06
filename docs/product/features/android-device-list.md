# Android device list screen

**Area:** UI
**Status:** `scoped`
**GitHub Issues:** [#7](https://github.com/khmelevartem/tether/issues/7)

---

## Why

On Android, Tether can already find peers on the Wi-Fi, but the user has no way to see them — there is no UI yet, only logs. Without a visible list, none of the rest of Tether is reachable from a phone: the user cannot pick a target, so cannot send a file. This screen turns "Tether works on Android" from a developer claim into something a real user can experience.

## What it does

When the user opens Tether on Android, they see a list of devices currently on their Wi-Fi that also run Tether. The list is live: devices appear as Tether finds them, and disappear when they leave the network. While nothing has been found yet, the screen explains that it is searching, so the user does not assume the app is broken or stuck.

Each row identifies one device clearly enough to pick the right one. Tapping a row is the entry point to sending a file — the picker and the transfer flow live in another feature; here it leads to a placeholder.

## User flows

**Primary flow**

1. User opens Tether on Android.
2. The screen shows "Searching for devices…" with a progress indicator.
3. Within a few seconds, devices on the same Wi-Fi appear as cards in the list.
4. The user taps a device → goes to the next step (file send, owned by another feature).

**Alternative paths**

- **No peers found.** The "Searching…" state stays visible. Nothing is shown as an error — a quiet network is not a failure.
- **Peer leaves the network.** Its card disappears from the list without disturbing the rest of the list.
- **User rotates the screen.** The list and the search state survive the rotation; nothing flickers and discovery does not start over.
- **Two devices share a display name.** Both are shown; the row carries enough additional information to tell them apart.

## What "working" looks like

- Opening the app on an Android phone shows a "Searching…" state immediately, not a blank screen.
- A laptop running Tether on the same Wi-Fi shows up in the list within roughly five seconds of opening the app.
- Turning that laptop off makes its card disappear from the phone's list within a few seconds.
- Rotating the phone while the list is populated does not blank the list or restart the search.
- A one-minute idle session on the screen produces no crashes, no freezes, and no error toasts.
- Tapping a device leads somewhere — a placeholder is acceptable for this feature, a dead tap is not.

## Not in this feature

- Picking a file, sending it, and showing transfer progress — separate feature.
- Showing incoming transfers or notifications.
- The same screen on iOS, macOS, or desktop — separate features per platform.
- Custom visual design or theming. Default look is enough.
- Pairing prompts and trust UI — owned by the pairing features.

## Open product questions

- Sorting order in the list. Discovery order is the default proposal; alternatives (alphabetical, last-used-first) become interesting once there are enough peers to scroll. Revisit when usage shows it.
- What identifying detail beyond the device name belongs on each row, if anything. Two laptops named "MacBook" should be distinguishable, but the right detail is not obviously the IP for a non-technical user.
