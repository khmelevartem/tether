# UX brief — File transfer (sender side)

**Spec:** [spec.md](spec.md)
**Status:** `draft`
**Scope:** Android sender + Desktop (JVM) sender. iOS sender, macOS sender, and all receive-side surfaces are out of scope and covered by separate future briefs.

---

## Closed product questions

The following questions were left open in the spec. This brief closes them; each is marked `(decided here)`.

**Soft threshold for folder-send confirmation** `(decided here)`
Show the "About to send N files, X GB. Continue?" confirmation when the selection exceeds **500 files OR 2 GB** total. Rationale: 500 files covers the realistic worst-case of a photo album or project directory without catching routine multi-file work; 2 GB catches large individual files and combined media batches before the user commits an unexpectedly long transfer. Either condition alone triggers the dialog — both thresholds apply independently.

**Aggregate progress visual shape** `(decided here)`
One `•—•` mark centered in the upper region of the progress screen, with the connecting line filling left-to-right in `accent` proportional to total bytes transferred across the whole batch. Directly below the mark: the current file name in `bodyMedium`, then the byte progress row in `numeric` style ("12.3 MB of 48.7 MB · 2.1 MB/s"). A single progress representation is cleaner than two stacked bars, and the current-file label gives granularity without a second bar. Per-file failures inside a batch are not shown during the run; they surface only in the end-of-batch summary.

**Save-folder substructure** `(decided here)`
Flat structure per platform downloads root: `Downloads/Tether/<filename>` on Android and Desktop. No per-peer subdirectory. Rationale: a per-peer folder (`Tether/<peer-name>/`) adds a navigation layer without user-visible benefit at MVP volumes; peer names change (rename, new device, factory reset) and create orphaned directories. File-name collision is already handled by the `name (1).ext` numbering scheme in the spec, which is the correct disambiguation mechanism. If usage data later shows that mixing from multiple peers creates confusion, the subdirectory approach can be layered in without breaking existing saves.

---

## Information architecture

The sender flow introduces two new screens and one dialog, sitting between the existing device-list surface and the (out-of-scope) receive-side surface.

```
DeviceListScreen
    │
    ├── [tap peer, in-app]
    │       └── OS file/folder picker (system-owned, not a Tether screen)
    │                └── TransferProgressScreen
    │                        └── CancelConfirmDialog (inline)
    │                                └── TransferSummaryScreen
    │
    └── [share-sheet / ACTION_SEND intent — Android only]
            └── DeviceListScreen (with pending-files banner)
                        └── (same picker → progress → summary path)
```

**Desktop drag-and-drop** enters the flow at a drop zone on `DeviceListScreen`, then continues to `TransferProgressScreen` and `TransferSummaryScreen` in the same way.

Screen identifiers introduced:
- `TransferProgressScreen`
- `TransferSummaryScreen`

Screen identifiers modified (entry-point addition only):
- `DeviceListScreen` — gains a "pending files" banner for the share-sheet path (Android) and a drag-and-drop drop zone (Desktop).

---

## Screens

### DeviceListScreen (modifications — entry-point additions only)

This brief does not redesign DeviceListScreen. It specifies only the two new entry-point behaviors grafted onto the existing screen.

#### Android — share-sheet entry (pending-files banner)

**Purpose.** Tell the user that files are already chosen and they only need to pick a destination device.

**Entry point.** System share-sheet / "Open with" → Tether (`ACTION_SEND` / `ACTION_SEND_MULTIPLE` intent received by `MainActivity`).

**Layout addition.** A non-dismissable banner pinned at the top of the list (above the first device row, below any top bar). The banner contains:
- A count summary: "1 file ready to send" / "N files ready to send".
- No file names in the banner — list is too long and the user already chose them.
- No explicit cancel button in the banner itself; the user can use the OS back gesture to abandon the intent.

**States.**
- `pending-single`: "1 file ready to send — pick a device."
- `pending-multi`: "3 files ready to send — pick a device."
- The searching / empty / error states of the device list itself are unchanged; the banner persists on top of all of them.

**Interactions.**
- Tapping a device row while the banner is showing skips the in-app file picker and goes directly to `TransferProgressScreen` with the files from the intent preloaded.
- System back gesture: dismisses Tether; the pending intent is abandoned. No in-app confirmation — the OS back gesture is the natural cancel here.

**Copy.**
- `"1 file ready to send — pick a device."`
- `"{N} files ready to send — pick a device."`

**Per-platform deltas.**
- Android: as described above.
- Desktop: N/A (no share-sheet on Desktop).
- iOS / macOS: out of scope.

**Accessibility.**
- Banner region has a semantic role of "status" / announcement so screen readers surface it immediately when the screen loads in this state.
- The banner text is the full readable string; no icon-only affordance.

---

#### Desktop — drag-and-drop drop zone

**Purpose.** Allow the user to initiate a send by dropping files or folders onto the Tether window rather than using the system file dialog.

**Entry point.** User drags files or a folder from their OS file manager onto the Tether window while `DeviceListScreen` is visible.

**Layout addition.** The entire `DeviceListScreen` surface becomes a drop target. During an active drag (files hovering over the window), a full-window drop overlay appears on top of the device list: a dashed border inset from the window edges, and centered copy reading "Drop to send". When the drag leaves the window, the overlay disappears and the list is visible again.

**States.**
- `drag-idle`: no overlay; device list shows normally.
- `drag-hover`: full-window overlay with dashed border and "Drop to send" label; device list is dimmed beneath.
- `drag-hover-transfer-in-progress`: dropping while a transfer is running is not accepted. The overlay text changes to "Transfer in progress — wait to drop." The drop is rejected (no files queued). See also `TransferProgressScreen` drag behavior below.

**Interactions.**
- Drop while `drag-idle`: files are accepted; if the device list has exactly one peer visible, that peer is pre-selected but a device-picker step is still shown (no silent auto-send — spec invariant). If multiple peers are visible, the device list gains a "Now pick a destination" visual affordance (the existing peer rows become the selection step). Then flow continues to OS picker skip (files are already chosen from the drop) → `TransferProgressScreen`.
- Drop is always rejected while a transfer is in progress.
- Keyboard: drag-and-drop is pointer-only; no keyboard equivalent (file dialog is the keyboard path).

**Copy.**
- `"Drop to send"`
- `"Transfer in progress — wait to drop."`

**Per-platform deltas.**
- Desktop: as described.
- Android: N/A.

**Accessibility.**
- The drag overlay is decorative from a keyboard perspective; the affordance is communicated through the copy visible during hover.
- Screen reader: announce the overlay state change with "Drop zone active" when drag enters the window.

---

### TransferProgressScreen

**Purpose.** Show the user that a transfer is underway and give them enough information to know what is happening and stop it if they choose.

**Entry points.**
- From `DeviceListScreen`: user tapped a peer (in-app flow) → OS file/folder picker returned one or more files → transfer begins.
- From `DeviceListScreen` with share-sheet intent: user tapped a peer while the pending-files banner was active.
- From `DeviceListScreen` via Desktop drag-and-drop: files dropped, peer selected.
- From `TransferSummaryScreen`: user tapped "Retry" on a failed file (resumes as a single-file batch to the same peer).

**Layout.**
- Top bar: back/close affordance (leading), peer name in `textPrimary` / `titleMedium` (center). No overflow menu.
- Upper region (centered vertically in roughly the top half): the `•—•` brand mark in its transfer-progress state — line filling left-to-right in `accent` proportional to total bytes transferred. Mark is the primary visual focus.
- Below the mark, centered:
  - Current file name in `bodyMedium` / `textMuted`. Long names are truncated in the middle with an ellipsis: `"Vacation photos…IMG_4721.jpg"`. Semantic label carries the full name for screen readers.
  - Byte-progress row in `numeric` style: `"12.3 MB of 48.7 MB · 2.1 MB/s"`. Updates at most once per second to avoid jitter.
- Lower region: "Cancel" button, full-width on mobile, centered and width-capped on Desktop, using `accent` text on `surface` background (not a filled button — the send action is already in progress; cancel is the only affordance and it is destructive).

**States.**

`preparing`
The OS picker has closed and Tether is establishing the connection but has not sent the first byte yet. The `•—•` mark shows searching state (hollow right dot, opacity pulse). Below the mark: `"Connecting to <peer name>…"`. No byte row yet. Cancel button is visible and active.

`in-progress`
Transfer is running. Mark is in transfer-progress state. Current file name and byte row are visible and updating. Cancel button is active.

`peer-dropped`
The peer disappeared from the network mid-transfer (mDNS loss or connection refused). Mark freezes at the failure point — line filled to the proportion reached, right dot hollow in `error` color (brand-mark error state).
Copy below mark: `"<peer name> is no longer reachable."`
Two buttons: "Retry" (primary, `accent`) and "Done" (secondary, `textMuted`).
Retry returns to `preparing` state with the same file set, attempting reconnection. Done navigates to `TransferSummaryScreen` with the failure recorded.

`connection-lost`
Wi-Fi / network lost mid-transfer. Same visual shape as `peer-dropped`.
Copy: `"Connection lost. Try again when you're back on Wi-Fi."`
Buttons: "Retry" and "Done". Retry is enabled only when network connectivity returns; if not yet restored, "Retry" is shown as disabled with copy `"Waiting for Wi-Fi…"`.

`file-unreadable`
A single file in the batch could not be read (I/O error, permission revoked after pick). The transfer of that file is skipped; the batch continues to the next file. A brief non-blocking inline notice appears below the byte row: `"Couldn't read <filename> — skipping."` The `•—•` mark continues progressing on `accent`. At batch end the summary records this as a failed file.

`receiver-write-failed`
The receiver reported it could not save the file (disk full, storage error). Same visual treatment as `file-unreadable`: inline notice `"Couldn't save <filename> on <peer name>."`, batch continues, recorded in summary.

`cancelled`
Either side cancelled (see `CancelConfirmDialog`). Mark freezes. Copy: `"Cancelled."` Single "Done" button. Navigates to `DeviceListScreen` (not `TransferSummaryScreen` — a cancel is not a summary event).

`folder-confirm` (pre-transfer gate — shown before `preparing` when soft threshold exceeded)
This is a modal confirmation step, not a separate screen. See `FolderSendConfirmDialog` in Conceptual components. After confirmation, flow continues to `preparing`.

**Interactions.**
- "Cancel" button → opens `CancelConfirmDialog`.
- "Retry" → restarts transfer from the beginning for all not-yet-completed files.
- "Done" → navigates to `TransferSummaryScreen`.
- System back gesture / window close: treated the same as tapping "Cancel" — opens `CancelConfirmDialog` if transfer is in progress; navigates away if in a terminal state.
- Desktop: drag-and-drop onto the window while this screen is shown is rejected (see drop zone delta above).

**Copy.**
- `"Connecting to <peer name>…"`
- `"<filename>"` (current file, truncated in the middle if long)
- `"12.3 MB of 48.7 MB · 2.1 MB/s"` (numeric format, see note below)
- `"Cancel"`
- `"Retry"`
- `"Done"`
- `"<peer name> is no longer reachable."`
- `"Connection lost. Try again when you're back on Wi-Fi."`
- `"Waiting for Wi-Fi…"`
- `"Couldn't read <filename> — skipping."`
- `"Couldn't save <filename> on <peer name>."`
- `"Cancelled."`

**Byte-progress format note.** Sizes use adaptive units: bytes below 1 KB shown as "N B", kilobytes as "N.N KB", megabytes as "N.N MB", gigabytes as "N.N GB". Speed uses the same adaptive format with "/s" suffix. Separator between size and speed is a centered dot `·` (U+00B7), consistent across Android and Desktop. All numeric values use the `numeric` type style (tabular figures).

**Per-platform deltas.**
- Android: "Cancel" button respects the 48dp minimum touch target. System back button / gesture opens `CancelConfirmDialog` identically to the on-screen Cancel button.
- Desktop: "Cancel" button is centered and width-capped (not edge-to-edge). Window close button (title bar X) triggers `CancelConfirmDialog` rather than immediately closing — the window close is intercepted while a transfer is in progress only.
- iOS / macOS: out of scope.

**Accessibility.**
- `•—•` mark: semantic label `"Transfer progress: {N}% complete"` (derived from byte ratio). Updates periodically, not on every frame.
- Current file name: semantic label reads the full untruncated name even when the display is truncated.
- Byte-progress row: semantic label `"{transferred} of {total} transferred, current speed {speed}"`.
- "Cancel" button: semantic label `"Cancel transfer"`.
- "Retry" button: semantic label `"Retry transfer"`.
- Focus order (Desktop): top bar close → Cancel → Retry (when visible) → Done (when visible).

---

### CancelConfirmDialog

**Purpose.** Prevent accidental cancellation of an in-progress transfer with a brief confirmation step.

**Entry point.** User taps "Cancel" on `TransferProgressScreen`, or triggers the system back gesture / window close while a transfer is in progress.

**Layout.** Modal dialog, centered on screen. Contains:
- Title: `"Cancel transfer?"`
- Body: `"The transfer will stop and any files not yet sent will not arrive."`
- Two buttons: "Stop transfer" (destructive, `error` text) and "Keep sending" (default, `accent` text or subtle style).

**States.**
- Single state — the dialog is either showing or dismissed.

**Interactions.**
- "Stop transfer" → cancels the transfer; dismisses dialog; `TransferProgressScreen` transitions to `cancelled` state.
- "Keep sending" → dismisses dialog; transfer continues from where it was; `TransferProgressScreen` returns to `in-progress` state.
- Tapping outside the dialog / pressing Escape (Desktop) → same as "Keep sending" — dismiss without cancelling.
- System back gesture (Android): same as "Keep sending".

**Copy.**
- `"Cancel transfer?"`
- `"The transfer will stop and any files not yet sent will not arrive."`
- `"Stop transfer"`
- `"Keep sending"`

**Per-platform deltas.**
- Android: dialog uses the rounded-rectangle shape from `TetherShapes.md`; dismiss on outside tap.
- Desktop: Escape key dismisses as "Keep sending"; Enter key confirms focused button.
- iOS / macOS: out of scope.

**Accessibility.**
- Dialog is announced as a modal alert on focus entry.
- Default focus lands on "Keep sending" (safe default) so Enter does not accidentally cancel.
- "Stop transfer" requires deliberate navigation to activate.

---

### TransferSummaryScreen

**Purpose.** Give the user a clear record of what happened — confirming success or explaining what failed and offering immediate recovery actions.

**Entry points.**
- From `TransferProgressScreen` on natural completion (all files processed — succeeded or failed individually).
- From `TransferProgressScreen` via "Done" button in a terminal error state (`peer-dropped`, `connection-lost`).

**Layout.**
- Top bar: close/back affordance (leading), title `"Transfer complete"` (center). No overflow menu.
- Upper region: `•—•` mark in success state (if at least one file succeeded) or error state (if all files failed).
- Summary line: `"Sent N of M to <peer name>."` in `titleMedium`.
- If all succeeded (N = M): no further content below the summary line. A "Done" button at the bottom returns to `DeviceListScreen`.
- If some failed (N < M): below the summary line, a scrollable list of failed-file rows. Each row shows the file name and a brief failure reason label ("Unreadable", "Couldn't save", "Connection lost"). Each row has a "Retry" action (tapping sends that file again as a single-file batch to the same peer — re-enters `TransferProgressScreen`). At the bottom: "Done" button returns to `DeviceListScreen`.

**States.**

`all-success`
`•—•` in success state. Copy: `"Sent {N} file to <peer name>."` / `"Sent {N} files to <peer name>."`. Single "Done" button.

`partial-failure`
`•—•` in success state (some files succeeded). Copy: `"Sent {N} of {M} to <peer name>. {K} failed:"`. Below: list of failed-file rows with per-file "Retry". "Done" button at bottom.

`all-failed`
`•—•` in error state. Copy: `"Couldn't send any files to <peer name>."`. Below: list of all files with their failure reason. Per-file "Retry". "Done" button at bottom.

`connection-error-summary`
Used when the transfer was terminated by `peer-dropped` or `connection-lost` before all files were processed. Same layout as `partial-failure` but the failure reason for all unprocessed files is `"Connection lost"`. "Retry all" button (sends all unprocessed files again to the same peer) appears above the per-file list alongside "Done".

**Interactions.**
- "Done" → navigates back to `DeviceListScreen`.
- Per-file "Retry" → navigates to `TransferProgressScreen` with that single file queued to the same peer. On completion, returns to an updated `TransferSummaryScreen` (the retried file is removed from the failed list if it succeeded).
- "Retry all" (connection-error-summary state only) → re-sends all unprocessed files as a new batch to the same peer.
- System back gesture / window close → same as "Done".

**Copy.**
- `"Transfer complete"` (top bar title)
- `"Sent {N} file to <peer name>."` / `"Sent {N} files to <peer name>."`
- `"Sent {N} of {M} files to <peer name>. {K} failed:"`
- `"Couldn't send any files to <peer name>."`
- `"Retry"` (per-file)
- `"Retry all"`
- `"Done"`
- Failure reason labels (inline, `textMuted`): `"Unreadable"`, `"Couldn't save"`, `"Connection lost"`

**Per-platform deltas.**
- Android: default; system back returns to `DeviceListScreen`.
- Desktop: window close returns to `DeviceListScreen`; no interception (transfer is done).
- iOS / macOS: out of scope.

**Accessibility.**
- `•—•` mark: semantic label `"Transfer succeeded"` or `"Transfer failed"` based on state.
- Failed-file list: each row has a semantic label `"<filename>, failed: <reason>. Retry button."`.
- "Retry" buttons within the list must each have distinct labels: `"Retry sending <filename>"`.
- Focus order (Desktop): top bar close → summary content (read-only) → per-file Retry buttons in list order → Retry all (if present) → Done.

---

## Flows

### Flow 1 — In-app send, all files succeed (Android and Desktop)

1. User is on `DeviceListScreen`, peers are visible.
2. User taps a peer row (Android) or clicks it (Desktop).
3. *(If folder pick and selection exceeds soft threshold: `FolderSendConfirmDialog` appears. User taps "Continue". Dismissed.)* 
4. OS file/folder picker opens (system-owned). User selects one or more files or a folder.
5. Picker closes. `TransferProgressScreen` appears in `preparing` state.
6. Connection established. Screen transitions to `in-progress`.
7. `•—•` mark line fills left-to-right. Current file name and byte row update as transfer progresses.
8. Last file completes. `•—•` plays success animation.
9. Screen navigates automatically to `TransferSummaryScreen` in `all-success` state.
10. User taps "Done" → returns to `DeviceListScreen`.

### Flow 2 — In-app send, partial failure (Android and Desktop)

1–7. Same as Flow 1.
8. A file in the batch fails (unreadable or receiver write fail). Inline notice appears below byte row. Batch continues.
9. Batch finishes. `TransferSummaryScreen` appears in `partial-failure` state.
10. User taps "Retry" on a failed file → `TransferProgressScreen` for that file.
11. On completion → `TransferSummaryScreen` updated (file removed from failed list if succeeded).
12. User taps "Done" → returns to `DeviceListScreen`.

### Flow 3 — Android share-sheet entry

1. User is in another app (Files, Photos, browser), taps Share → Tether.
2. Tether opens. `DeviceListScreen` loads with the pending-files banner.
3. User taps a peer → skips the file picker (files are already known).
4. `TransferProgressScreen` appears in `preparing` state.
5. Continues as Flow 1 from step 6.

### Flow 4 — Desktop drag-and-drop entry

1. User drags files/folder from the OS file manager over the Tether window.
2. Drop overlay appears on `DeviceListScreen` with "Drop to send".
3. User drops.
4. If multiple peers: device list shows with a "Now pick a destination" affordance; user clicks a peer.
5. File picker step is skipped (files are from the drop).
6. `TransferProgressScreen` in `preparing` state.
7. Continues as Flow 1 from step 6.

### Flow 5 — Connection lost mid-transfer

1–6. Same as Flow 1.
7. Network is lost. `TransferProgressScreen` transitions to `connection-lost` state. Mark freezes at failure point (error state). Copy: `"Connection lost. Try again when you're back on Wi-Fi."`
8. "Retry" button is disabled with label `"Waiting for Wi-Fi…"` until connectivity returns.
9. Network restored. "Retry" becomes active.
10. User taps "Retry" → back to `preparing` state. Attempts to reconnect and restart the batch.
   — OR —
10. User taps "Done" → `TransferSummaryScreen` in `connection-error-summary` state.

### Flow 6 — Cancel mid-transfer

1–6. Same as Flow 1.
7. User taps "Cancel" or system back. `CancelConfirmDialog` appears.
8a. "Keep sending" → dialog dismissed, transfer continues.
8b. "Stop transfer" → transfer halted. `TransferProgressScreen` transitions to `cancelled`. Copy: `"Cancelled."` with "Done" button.
9. "Done" → `DeviceListScreen`.

### Flow 7 — Folder send with soft-threshold confirmation

1. User taps a peer on `DeviceListScreen`.
2. OS folder picker opens. User selects a folder.
3. Tether counts files and total size. If exceeds 500 files OR 2 GB: `FolderSendConfirmDialog` appears. Copy: `"About to send {N} files ({X} GB). Continue?"` with "Continue" and "Cancel".
4a. "Continue" → `TransferProgressScreen` in `preparing`.
4b. "Cancel" → dismisses dialog; OS picker can be reopened or user returns to device list.

---

## Navigation

Both `TransferProgressScreen` and `TransferSummaryScreen` are push-navigated on top of `DeviceListScreen`. The back stack is:

```
DeviceListScreen → TransferProgressScreen → TransferSummaryScreen
```

- `DeviceListScreen` stays alive under the stack; returning to it does not re-trigger discovery.
- `CancelConfirmDialog` and `FolderSendConfirmDialog` are modal overlays on `TransferProgressScreen`, not separate back-stack entries.
- On completion or "Done" from `TransferSummaryScreen`, the stack pops all the way back to `DeviceListScreen` — not to `TransferProgressScreen`.
- On Android, pressing the system back button from `TransferProgressScreen` during a transfer triggers `CancelConfirmDialog`. In terminal states (`cancelled`, `peer-dropped` resolved with "Done"), back pops to `DeviceListScreen`.

---

## Conceptual components

The following distinct UI patterns are used across this brief. Naming is conceptual — `ui-expert` maps each to a composable.

1. **Pending-files banner** — non-dismissable top banner on the device list communicating that files from an external intent are preloaded. Android-only.
2. **Full-window drop overlay** — full-screen drag-and-drop receiving surface with dashed border and centered label. Desktop-only. Appears on top of the device list during an active drag.
3. **Transfer progress mark** — the `•—•` brand mark in its transfer-progress state (line filling left-to-right in `accent`), sized for use as a primary screen element. Reads byte ratio as its input.
4. **Byte-progress row** — a single line of `numeric`-styled text combining transferred size, total size, and current speed, with the `·` separator. Updates at most once per second.
5. **Current-file label** — `bodyMedium` text showing the current file name, truncated in the middle when too long, with the full name exposed to screen readers.
6. **Inline transfer notice** — a transient, non-blocking notice row below the byte row for per-file errors (unreadable, receiver write fail). Appears briefly, then fades. Not a toast — it is inline and does not obscure controls.
7. **Cancel button** — a text-style (not filled) button using `accent` color, full-width on mobile, centered-and-capped on Desktop. Semantically labelled "Cancel transfer".
8. **Cancel-confirm dialog** — a two-action modal dialog with a destructive primary action ("Stop transfer") in `error` color and a safe default ("Keep sending"). Default focus is on the safe action.
9. **Folder-send confirm dialog** — a two-action modal dialog showing file count and total size before committing to a large folder send. Distinct from the cancel dialog: its destructive action is "Cancel" and its primary is "Continue".
10. **Transfer summary mark** — the `•—•` brand mark in success or error state, sized for use as a primary screen element at end-of-transfer. Encodes outcome (success vs. any-failure) in the mark's visual state.
11. **Summary line** — `titleMedium` text stating how many files were sent and to whom.
12. **Failed-file row** — a list row showing file name, failure reason label, and a per-file "Retry" action. Used in `TransferSummaryScreen`.
13. **Peer identity chip** — a compact identifier element showing the peer's name using `peerIdentity` color treatment, used in the top bar of `TransferProgressScreen` and `TransferSummaryScreen` to keep peer identity visually distinct from navigation elements.

---

## Open UX questions

None. All questions from the spec have been resolved in this brief. The following were the open items; each is now closed above:

- Soft threshold: **500 files OR 2 GB** (decided here, see top of brief).
- Aggregate progress visual shape: **one `•—•` mark + byte-progress row + current-file label** (decided here).
- Save-folder substructure: **flat `Downloads/Tether/<filename>`** (decided here).

The following are product-level questions outside UX scope, carried forward from the spec for the orchestrator to surface:

- **Auto-pick single online peer toggle.** The spec marks this as Post-MVP and default OFF. The banner text, undo timeout, and settings placement for this feature are not designed here and will be addressed when the toggle is scoped.
- **Linux notification fidelity.** Best-effort tap-to-reveal is accepted for MVP per spec. A future brief revision can address a third-party system-tray library path if prioritised.
