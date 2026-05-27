# File transfer — pick, send, see it arrive

**Area:** Transfer / UI
**Status:** `scoped`
**GitHub Issues:** [#8](https://github.com/khmelevartem/tether/issues/8) (Android send UI), [#81](https://github.com/khmelevartem/tether/issues/81) (iOS FileServer receive); iOS send UI, Desktop send UI, receive-side UI — _tbd_

---

## Why

After picking a peer on the [device list](../device-list/spec.md) (and confirming a [pairing](../pairing/spec.md) PIN if it's a first encounter), the user has to actually move bytes and see what is happening on both ends. Discovery and pairing without a transfer surface are an unfinished bridge.

This feature is also where Tether's two transport promises from [vision.md](../../vision.md) have to land in user-visible behaviour: **original bytes, untouched** (no compression, no conversion, no resizing) and **any size, any number** (no in-memory buffering, no silent size caps, N≥1 files in one send). They apply to every direction and every platform.

## What it does

The user picks one or more files — or a folder — and sends them. They see progress on their screen and on the receiver's screen. When done, the receiver gets an OS notification that opens the saved location with one tap (where the platform permits). At no point are bytes touched: what was picked is exactly what lands.

There are two equally supported entry points:

- **From inside Tether** — Send → pick peer → pick files / folder.
- **From the system share sheet / "Open with"** — the user is already in the OS file browser, gallery, or any app holding a file, taps Share, and chooses Tether. The file is already selected; flow continues at the device list.

Sending N files is the same surface as sending one. Sending a folder is N files with structure preserved. The receiver always knows what is arriving and from whom, can cancel mid-flight, and never ends up with a partial file pretending to be complete.

## User flows

**Primary — in-app, single peer, N≥1 files**

1. User taps Send on a peer from the device list.
2. System file picker opens. User selects one or more files (or a folder).
3. If this is a first encounter with that peer, the [pairing](../pairing/spec.md) PIN dialog appears on both sides; user confirms.
4. Progress screen shows current file name, byte progress across the whole batch, and current speed.
5. On the receiver, a matching incoming-transfer surface appears with the same information.
6. On completion, sender shows "Sent N files to <peer>". Receiver shows an OS notification; tapping it opens the saved folder (or — where the OS forbids that — Tether's own "Received" screen with the path).

**Primary — system share sheet / "Open with"**

1. User is in Files / Photos / any source app holding one or more files. Taps Share → Tether.
2. Tether opens at the device list, the file(s) already selected.
3. Flow continues from step 3 of the in-app primary flow.

**Alternative paths**

- **Single online paired peer (Post-MVP toggle).** When the user enables "Send to my only online device" and exactly one paired peer is currently visible in the mDNS cache, the device-list step is skipped after Share. A short banner says "Sending to <peer>" with an inline Cancel that returns the user to the device list. Default OFF; toggle in settings. Hard auto-skip is never the default because surprise sends are worse than an extra tap.
- **Folder send.** Picking a folder sends its contents recursively. Structure is preserved relative to the picked folder's root: `Vacation/2024/IMG_001.jpg` arrives at `Tether/Vacation/2024/IMG_001.jpg`. Empty folders and hidden files (`.DS_Store`, `Thumbs.db`, dotfiles) are skipped. Symlinks are followed with cycle detection. Order of files within the batch is the order returned by the OS picker — Tether does not re-sort. Before starting, Tether shows a confirmation if the selection exceeds a soft threshold (number of files / total size) — guards against accidentally picking the disk root.
- **Cancel by sender.** Sender taps Cancel mid-transfer. Both sides stop. Receiver discards any partial file silently — no leftover.
- **Cancel by receiver.** Receiver taps Cancel on their incoming card. Both sides stop. Same partial-file rule.
- **Concurrent incoming from different peers.** Two paired peers send to the receiver at the same time. Both transfers proceed in parallel; the receiver UI stacks the two incoming cards.
- **Name collision on the receiver.** A file with the same name already exists in the destination. The incoming file is saved as `name (1).ext`, `name (2).ext`, etc. The sender's bytes are never silently overwritten and never refused.
- **Untrusted (not paired) peer attempts to send.** Tether routes through the pairing PIN flow first — surface owned by [pairing](../pairing/spec.md). The file-transfer surface only appears after PIN confirmation on both sides.

**Failure surfaces (what the user sees, what they can do)**

| Trigger | User-visible message | Retry available |
|---|---|---|
| Wi-Fi / network lost mid-transfer | "Connection lost. Try again when you're back on Wi-Fi." | Yes |
| Peer becomes unreachable mid-transfer (drops from mDNS, connection refused) | "<peer> is no longer reachable. Try again." | Yes — semantically the same as connection-lost |
| Single file unreadable on sender (I/O error, permission revoked) | "Couldn't read <filename>." | Yes — user can re-pick |
| Receiver write fails (disk full, storage error) | "Couldn't save on <peer>. Free up space and try again." | Yes — user can free space on receiver, then retry |
| Cancelled by either side | "Cancelled" — neutral, not styled as error | — |
| Per-file failure inside a batch | Batch continues. End-of-batch summary: "Sent N of M. K failed: <names>" with per-file retry affordance | Per-file |

## What "working" looks like

- Single-file send, multi-file send, and folder send all work as one consistent surface — the user does not distinguish between them.
- A file of any size goes through without out-of-memory errors. Streaming, not buffering.
- The bytes on the receiver are byte-identical to the sender's source. No compression, no conversion, no resizing.
- Cancel from either side stops the transfer cleanly and leaves no partial file pretending to be complete.
- Per-file failure inside a batch does not abort the batch — the remaining files still go.
- Progress is shown in bytes (current file name + total batch bytes + current speed), not in file count percentage.
- On completion, the receiver gets an OS notification that — where the platform permits — opens the saved folder with one tap.
- Both entry points (in-app, system share sheet / "Open with") reach the same flow and the same result.
- A user with the optional "auto-pick single online peer" toggle ON and exactly one paired peer online never sees the device-list step after Share; a banner names the chosen peer with an inline undo.

## Platform notes

### Android

- **Send UI entry points:** in-app system photo picker for visual media and the system file / folder picker for everything else. OS share sheet — mandatory equivalent entry point.
- **Receive-side background.** Receive runs inside a foreground service ([permissions strategy](../system/permissions/spec.md), [android-fgs.md](../../../knowledge/android-fgs.md)). On Android 15+ the OS enforces a cumulative ~6h/day cap on the foreground-service category Tether uses; this is an OS limit, not a Tether limit. Long-running receivers may need to be restarted by the user from the persistent notification.
- **Save location:** `Downloads/Tether/`, visible in the system Files app and the system Downloads UI without extra permissions.
- **Notification → reveal:** tapping the completion notification opens the system Files app at the saved location.
- **Permissions** (media permissions on modern Android) — owned by [permissions strategy](../system/permissions/spec.md).

### iOS

- **Send UI entry points:** the system Photos picker and the system Files picker, both available — user chooses which source per send. Folder picking is through the system Files picker. OS share sheet — mandatory equivalent entry point.
- **Foreground-active only.** iOS does not permit listening sockets, custom servers, or arbitrary local-network browsing in the background. Both sending and receiving require Tether to be in the foreground; screen lock interrupts an active transfer. This is an architectural limit of iOS, not a Tether bug. See [ios-background-networking.md](../../../knowledge/ios-background-networking.md) for the full constraint analysis and the asymmetric Post-MVP sender-only path.
- **Receiver after suspension / screen lock.** Any in-flight inbound transfer dies when the OS suspends Tether — no completion notification fires (the app is not running to fire it), and any partial file is discarded per the no-partial-file rule. On next foreground, Tether surfaces a one-time "Transfer from <peer> was interrupted" entry so the receiver knows they need to ask the sender to retry. The sender simultaneously sees the standard "<peer> is no longer reachable" failure.
- **Save location:** the app's `Tether/` folder, exposed as `On My iPhone → Tether/` in the system Files app.
- **Notification → reveal:** tapping the OS notification opens Tether. iOS does not let third-party apps deep-link the Files app at a specific path, so the realistic equivalent is an in-app "Received" screen naming the path with a "Show in Files" button that surfaces the save folder through the system Files picker.

### macOS

- **Send UI entry points:** the system file open dialog (in-app) and the system share menu — both supported.
- **Save location:** `~/Downloads/Tether/`.
- **Notification → reveal:** tapping the system notification activates Tether, which reveals and selects the file in Finder.

### Desktop JVM (Windows, Linux)

- **Send UI entry points:** in-app system file dialog. No OS-level "Send To" / share integration in MVP — desktop users open Tether and pick. Drag-and-drop onto the window is an equally natural entry point.
- **Save location:** `Downloads/Tether/` under the user's home folder.
- **Notification → reveal:** Windows — tapping the system-tray notification opens File Explorer at the file's parent folder; selecting the specific file inside the folder is not guaranteed and the user lands on the right directory. Linux — best-effort; both system-tray notification click actions and "reveal in file manager" vary by desktop environment, with GNOME particularly fragile. Fallback is opening the parent folder without selection; on some DEs the notification click may silently no-op.

## Not in this feature

- **Resume after interrupted transfer.** Post-MVP, see [roadmap.md](../../roadmap.md).
- **iOS background sending or receiving.** Architecturally constrained by iOS; see [ios-background-networking.md](../../../knowledge/ios-background-networking.md). A sender-only URLSession-background path remains a conditional Post-MVP option.
- **Pairing PIN dialog.** Owned by [pairing](../pairing/spec.md). File-transfer surface only appears after PIN confirmation.
- **Permission prompts** (media permissions on Android, Local Network on iOS, etc.). Owned by [permissions strategy](../system/permissions/spec.md).
- **Fan-out: one file → N different peers in one action.** Different mechanism, per-peer pairing state, per-peer failure surfaces. Separate feature, Post-MVP — and a candidate for [monetization](../../monetization.md).
- **Folder sync** (watched, continuous). Different product surface, Post-MVP and Pro candidate per [monetization](../../monetization.md).
- **Sleep / suspend handling on macOS and Desktop.** OS sleep tears down sockets; Tether does not engineer around this for MVP. Document but don't compensate.
- **"Send To" / shell-verb integration on Windows or Linux desktops.** OS-level entry points beyond in-app picker and drag-and-drop are not in MVP scope.

## Open product questions

- **Soft threshold for folder-send confirmation.** Resolved in [`ux-brief.md`](ux-brief.md): `>500 files OR >2 GB`.
- **Aggregate progress visual shape.** Resolved in [`ux-brief.md`](ux-brief.md): single brand-mark indicator in its transfer-progress state, filename and speed beneath; no ETA.
- **"Auto-pick single online paired peer" toggle.** Resolved in [`ux-brief.md`](ux-brief.md): default OFF; first-time inline prompt at the top of the device list when the single-peer condition is met; flipping ON triggers a confirm dialog and persists the preference in Settings.
- **Concurrent incoming from different peers.** MVP accepts them in parallel; receiver UI stacks two cards. Whether this stays free forever or whether a "many-at-once" capability is a future Pro shape (e.g., bulk inbox triage) is open — folded into the broader [monetization](../../monetization.md) read once usage signal exists.
- **Linux completion-notification fidelity.** Tap-to-reveal-in-file-manager is best-effort on Linux because of DE variation. Whether to ship a third-party system-tray library Post-MVP for parity, or accept the OS-fallback variance, is open.
- **Save-folder name and structure.** `Tether/` under each platform's downloads location is the proposal. Whether to add `Tether/<peer-name>/` sub-grouping (so files from Lena's laptop don't mix with files from the work iMac) is open — useful but adds an extra layer the user must navigate. Ux-expert must decide.
- **Receiver-side retry for partial-failure.** Sender-side retry is in scope for this feature; receiver-initiated retry is deferred. A receiver-side retry — after the user frees up space — would let the receiver itself ask the sender to re-send only the failed files. Open: protocol shape (pull from sender vs sender re-initiates), how long the failed-batch reference persists, and whether the sender must still be online. Folded into a Post-MVP look once the on-sender flow ships.
