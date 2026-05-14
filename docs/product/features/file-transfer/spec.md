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
- **Folder send.** Picking a folder sends its contents recursively. Structure is preserved relative to the picked folder's root: `Vacation/2024/IMG_001.jpg` arrives at `Tether/Vacation/2024/IMG_001.jpg`. Empty folders and hidden files (`.DS_Store`, `Thumbs.db`, dotfiles) are skipped. Symlinks are followed with cycle detection. Before starting, Tether shows a confirmation if the selection exceeds a soft threshold (number of files / total size) — guards against accidentally picking the disk root.
- **Cancel by sender.** Sender taps Cancel mid-transfer. Both sides stop. Receiver discards any partial file silently — no leftover.
- **Cancel by receiver.** Receiver taps Cancel on their incoming card. Both sides stop. Same partial-file rule.
- **Per-file failure in a batch.** A single file fails (unreadable, write error, etc.). The batch continues with the remaining files. At the end the sender sees "Sent 27 of 30. 3 failed: <names>" with a per-file retry affordance.
- **Concurrent incoming from different peers.** Two paired peers send to the receiver at the same time. Both transfers proceed in parallel; the receiver UI stacks the two incoming cards.
- **Name collision on the receiver.** A file with the same name already exists in the destination. The incoming file is saved as `name (1).ext`, `name (2).ext`, etc. The sender's bytes are never silently overwritten and never refused.
- **Untrusted (not paired) peer attempts to send.** Tether routes through the pairing PIN flow first — surface owned by [pairing](../pairing/spec.md). The file-transfer surface only appears after PIN confirmation on both sides.

## What "working" looks like

- Single-file send, multi-file send, and folder send all work as one consistent surface — the user does not distinguish between them.
- A file of any size goes through without out-of-memory errors. Streaming, not buffering.
- The bytes on the receiver are byte-identical to the sender's source. No compression, no conversion, no resizing. Verifiable by hash on both sides.
- Cancel from either side stops the transfer cleanly and leaves no partial file pretending to be complete.
- Per-file failure inside a batch does not abort the batch — the remaining files still go.
- Progress is shown in bytes (current file name + total batch bytes + current speed), not in file count percentage.
- On completion, the receiver gets an OS notification that — where the platform permits — opens the saved folder with one tap.
- Both entry points (in-app, system share sheet / "Open with") reach the same flow and the same result.
- A user with the optional "auto-pick single online peer" toggle ON and exactly one paired peer online never sees the device-list step after Share; a banner names the chosen peer with an inline undo.

## Platform notes

### Android

- **Send UI entry points:** in-app picker via `ActivityResultContracts.PickMultipleVisualMedia` (Photo Picker) and `OpenMultipleDocuments` / `OpenDocumentTree` (SAF) for photos and arbitrary files / folders respectively. OS share sheet via `ACTION_SEND` / `ACTION_SEND_MULTIPLE` intent-filter — mandatory entry point.
- **Receive-side background.** Receive runs inside a foreground service ([permissions strategy](../system/permissions/spec.md), [android-fgs.md](../../../knowledge/android-fgs.md)). On Android 15+ the OS enforces a cumulative ~6h/day cap on the FGS type Tether uses; this is an OS limit, not a Tether limit. Long-running receivers may need to be restarted by the user from the persistent notification.
- **Save location:** `Downloads/Tether/` via MediaStore. Visible in the system Files app and Downloads without extra permissions.
- **Notification → reveal:** tap on the completion notification opens the system Files app at the saved location with the file selected.
- **Permissions** (`READ_MEDIA_*` on API 33+) — owned by [permissions strategy](../system/permissions/spec.md).

### iOS

- **Send UI entry points:** Photos via `PHPickerViewController` and Files via `UIDocumentPickerViewController` — both available, user-selectable. Folder picking via `UIDocumentPickerViewController` with `.folder`. OS share sheet via Share Extension target — mandatory entry point.
- **Foreground-active only.** iOS does not permit listening TCP sockets, custom servers, or arbitrary mDNS browsing in the background. Both sending and receiving require Tether to be in the foreground; screen lock interrupts an active transfer. This is an architectural limit of iOS, not a Tether bug. See [ios-background-networking.md](../../../knowledge/ios-background-networking.md) for the full constraint analysis and the asymmetric URLSession-background path that is conditionally available Post-MVP for sender-only.
- **Save location:** Tether app container `Documents/Tether/`, exposed as `On My iPhone → Tether/` via `UIFileSharingEnabled` + `LSSupportsOpeningDocumentsInPlace` in Info.plist. Browseable in the Files app.
- **Notification → reveal:** OS notification tap opens Tether. iOS does not let third-party apps deep-link the Files app at a specific path, so the realistic equivalent is an in-app "Received" screen naming the path with a "Show in Files" button that opens a `UIDocumentPickerViewController` rooted at the save folder.

### macOS

- **Send UI entry points:** standard `NSOpenPanel` (in-app) and Share Extension for the system share menu — mandatory entry point.
- **Save location:** `~/Downloads/Tether/`.
- **Notification → reveal:** `UNUserNotificationCenter` tap activates the app, which calls `NSWorkspace.activateFileViewerSelecting` to reveal-and-select the file in Finder.

### Desktop JVM (Windows, Linux)

- **Send UI entry points:** in-app via standard file dialogs. No OS-level "Send To" / share integration in MVP — desktop users open Tether and pick. The in-app surface includes drag-and-drop onto the window as an equally natural entry point.
- **Save location:** `<user.home>/Downloads/Tether/`.
- **Notification → reveal:** Windows — system tray notification tap calls `Desktop.browseFileDirectory` to open Explorer with the file selected. Linux — best-effort; tray-notification click action and "reveal in file manager" both vary by desktop environment, with GNOME particularly fragile. Fallback is opening the parent folder without selection; on some DEs the notification click may silently no-op.

## Not in this feature

- **Resume after interrupted transfer.** Post-MVP, see [roadmap.md](../../roadmap.md).
- **iOS background sending or receiving.** Architecturally constrained by iOS; see [ios-background-networking.md](../../../knowledge/ios-background-networking.md). A sender-only URLSession-background path remains a conditional Post-MVP option.
- **Pairing PIN dialog.** Owned by [pairing](../pairing/spec.md). File-transfer surface only appears after PIN confirmation.
- **Permission prompts** (`READ_MEDIA_*`, Local Network on iOS, etc.). Owned by [permissions strategy](../system/permissions/spec.md).
- **Fan-out: one file → N different peers in one action.** Different mechanism, per-peer pairing state, per-peer failure surfaces. Separate feature, Post-MVP — and a candidate for [monetization](../../monetization.md).
- **Folder sync** (watched, continuous). Different product surface, Post-MVP and Pro candidate per [monetization](../../monetization.md).
- **Sleep / suspend handling on macOS and Desktop.** OS sleep tears down sockets; Tether does not engineer around this for MVP. Document but don't compensate.
- **"Send To" / shell-verb integration on Windows or Linux desktops.** OS-level entry points beyond in-app picker and drag-and-drop are not in MVP scope.

## Open product questions

- **Soft threshold for folder-send confirmation.** Above what file count / total size does Tether show a "About to send N files, X GB. Continue?" prompt? Numbers to be set during UX brief — likely a few thousand files or several GB.
- **Aggregate progress visual shape.** The product decision is byte-based progress with current file name and current speed. The exact arrangement (one bar vs. two, where the file name sits, whether failed files inside a batch are visible during the run or only at the end) is a UX-brief decision.
- **"Auto-pick single online paired peer" toggle.** Default OFF, opt-in. Banner text, undo timeout, what happens when the second paired peer comes online mid-flow — all UX-brief decisions. Whether the toggle lives in app settings or as a one-time prompt after the first paired-and-sent moment is also open.
- **Concurrent incoming from different peers.** MVP accepts them in parallel; receiver UI stacks two cards. Whether this stays free forever or whether a "many-at-once" capability is a future Pro shape (e.g., bulk inbox triage) is open — folded into the broader [monetization](../../monetization.md) read once usage signal exists.
- **Linux completion-notification fidelity.** Tap-to-reveal-in-file-manager is best-effort on Linux because of DE variation. Whether to ship a JNA-based system-tray library (e.g., `dorkbox/SystemTray`) Post-MVP for parity, or accept the OS-fallback variance, is open.
- **Save-folder name and structure.** `Tether/` under each platform's downloads location is the proposal. Whether to add `Tether/<peer-name>/` sub-grouping (so files from Lena's laptop don't mix with files from the work iMac) is open — useful but adds an extra layer the user must navigate.
