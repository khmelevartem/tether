# UX brief — File transfer

**Spec:** [spec.md](spec.md)
**Status:** `ready`

---

## Information architecture

This feature introduces six new screens / dialogs, touches one existing screen (DeviceListScreen), and adds one settings section.

```
DeviceListScreen  (existing — touched)
├── [banner: pending outbound]   ← AutoPickInlinePrompt (first-time only, at top)
├── [banner: active outbound]
├── IncomingTransferCard(s)      ← stacked, inline, above device rows
└── [tap peer] ──────────────────────────────────────┐
                                                      ▼
                              MobilePickerChooserSheet (Android + iOS only)
                                    │
                              [large folder?] ──── FolderSendConfirmDialog
                                    │
                              SendProgressScreen
                                    │
                              TransferSummaryScreen
                                         │ (iOS notification tap)
                              ReceivedScreen (iOS only, notification-tap entry)

AutoPickConfirmDialog  (modal, fired inline when toggle flipped ON)

SettingsSection — File Transfer  (inside the app's existing settings surface)
```

Screens introduced: SendProgressScreen, IncomingTransferCard, FolderSendConfirmDialog, TransferSummaryScreen, AutoPickInlinePrompt, AutoPickConfirmDialog, ReceivedScreen (iOS only), MobilePickerChooserSheet, SettingsSection — File Transfer.

Screens touched: DeviceListScreen.

---

## Screens

### DeviceListScreen

**Purpose.** The home screen where the user sees online peers and manages any in-flight or pending transfer state.

**Entry points.** App launch; OS share-sheet / "Open with" routing Tether to foreground with files pre-selected; return from SendProgressScreen or TransferSummaryScreen.

**Layout.**

- Top bar: app title, settings affordance.
- Non-dismissible pending/active banner (present only when a pending outbound or active outbound transfer exists) — rendered above device rows, below the top bar.
- AutoPickInlinePrompt strip (first-time only, see that screen) — rendered between the banner (if any) and the device rows.
- Scrollable list of device rows (searching / empty / populated — per existing spec).
- IncomingTransferCard(s) — stacked, rendered above device rows when inbound transfers exist.

**States.**

- **Searching (no peers yet, no pending transfer):** the `•—•` brand mark in its searching state (hollow right dot, opacity oscillation); copy: "Searching for devices…". No banner.
- **Searching (with pending outbound):** same searching animation; banner above reads "Ready to send \<N\> files (\<size\>). Pick a device below." with [Cancel] on the right.
- **Populated (no pending transfer):** device rows visible. No banner.
- **Populated (pending outbound):** device rows visible; banner reads "Ready to send \<N\> files (\<size\>). Pick a device below." with [Cancel].
- **Active outbound (transfer in flight from this device):** banner reads "Sending to \<peer\> — \<N\> files, \<progress\>%". [Cancel] on right. Device rows still visible beneath.
- **Active inbound (one transfer arriving):** IncomingTransferCard rendered above device rows.
- **Active inbound (two concurrent transfers):** two stacked IncomingTransferCards.
- **Banner success toast (4 s, after outbound completes):** banner transitions to success-tone: "Sent to \<peer\>", then self-dismisses.
- **Banner cancelled toast (4 s, after outbound cancelled):** banner transitions to neutral-tone: "Cancelled", then self-dismisses.
- **iOS foreground constraint banner (during any transfer):** persistent "Keep Tether open" banner rendered above the pending/active banner (or at the very top if no transfer banner exists). Copy: "Keep Tether open to complete the transfer." Not dismissible. Disappears when no transfer is active.

**Interactions.**

- Tap pending-banner [Cancel]: clears pending selection, dismisses banner (no confirm dialog). If a transfer was active, both sides stop immediately.
- Tap device row when pending outbound exists: initiates send to that peer (proceeds to MobilePickerChooserSheet on Android/iOS or directly to FolderSendConfirmDialog / SendProgressScreen on macOS/Desktop if files were already selected via share-sheet or drag-drop).
- Tap device row when no pending outbound: opens in-app picker flow (MobilePickerChooserSheet on mobile; system file dialog on macOS/Desktop), then proceeds.
- Long-press / right-click device row (Desktop/macOS): no additional action in this feature; out of scope.

**Copy.**

- "Searching for devices…"
- "Ready to send \<N\> files (\<size\>). Pick a device below."
- "Sending to \<peer\> — \<N\> files, \<progress\>%"
- "Sent to \<peer\>"
- "Cancelled"
- "Keep Tether open to complete the transfer." (iOS only)

**Per-platform deltas.**

- Android: default (banner + device rows; share-sheet entry sets pending state).
- iOS: adds persistent "Keep Tether open" foreground constraint banner during any transfer. Share-sheet entry sets pending state.
- macOS: drag-and-drop of files onto the Tether window sets pending state and shows the banner; no share-sheet entry needed for in-app picker (system file dialog). No foreground constraint banner.
- Desktop JVM: same as macOS for drag-and-drop. No share-sheet. No foreground constraint banner.

**Accessibility.**

- Pending/active banner is a live region (assertive); announces its content once when it first appears and when state changes.
- [Cancel] button semantic label: "Cancel pending transfer" or "Cancel active transfer to \<peer\>" depending on state.
- IncomingTransferCard(s) are live regions (polite) — see IncomingTransferCard accessibility section.
- Device rows: each row's semantic label includes the device name and type (e.g., "MacBook Air — online").
- iOS foreground constraint banner: role is `alert`; announced once when it appears.

---

### MobilePickerChooserSheet

**Purpose.** A bottom sheet on Android and iOS that lets the user choose which system picker to invoke — Photos, Files, or Folder — before a send flow begins.

**Entry points.** Tapping a device row on Android or iOS when no pending outbound exists (in-app send). Also reachable if a pending outbound was initiated in-app and the user needs to add more files (not in MVP scope — single batch only).

**Layout.**

- Bottom-sheet handle at top.
- Sheet title: "Send from…"
- Three options as tappable rows with icons: "Photos", "Files", "Folder".
- Swipe down or tap outside: dismisses without action.

**States.**

- **Default:** three options listed, no loading state.
- **Dismissed:** sheet gone, user returns to DeviceListScreen with no pending state.

**Interactions.**

- Tap "Photos": opens system photo picker (multi-select). On completion, sets pending outbound state and returns to DeviceListScreen with banner. Then navigates to SendProgressScreen after user taps the target device (or auto-picks if toggle ON).
- Tap "Files": opens system file picker (multi-select). Same on completion.
- Tap "Folder": opens system folder picker (single selection). Same on completion. If selected folder exceeds the soft threshold (>500 files OR >2 GB), FolderSendConfirmDialog appears before proceeding.
- Swipe down / tap scrim: sheet dismisses, no action.

**Copy.**

- "Send from…"
- "Photos"
- "Files"
- "Folder"

**Per-platform deltas.**

- Android: uses Storage Access Framework pickers. "Photos" maps to system photo picker; "Files" maps to SAF file picker; "Folder" maps to SAF folder picker.
- iOS: "Photos" maps to PHPickerViewController; "Files" and "Folder" map to UIDocumentPickerViewController (in the appropriate mode).
- macOS: this screen does not exist — system file dialog handles both files and folder (separate "Open" and "Open Folder" actions on the same dialog, or two distinct affordances on the SendProgressScreen entry).
- Desktop JVM: this screen does not exist — same as macOS.

**Accessibility.**

- Bottom sheet is presented as a modal; focus moves into the sheet on open.
- Each option row has a semantic label matching its visible label ("Photos", "Files", "Folder").
- Dismiss affordance (handle, scrim): semantic label "Dismiss picker chooser".
- Focus returns to the triggering device row on dismiss.

---

### FolderSendConfirmDialog

**Purpose.** A confirmation dialog that appears when the selected folder exceeds the soft threshold (>500 files OR >2 GB), guarding against an accidental "select all" scenario.

**Entry points.** After folder selection (from MobilePickerChooserSheet on Android/iOS, or system file dialog on macOS/Desktop), when the selection exceeds the threshold.

**Layout.**

- Modal dialog with title, body text, and two buttons.
- Title: "Large folder"
- Body: "About to send \<N\> files (\<size\>) to \<peer\>. Continue?"
- Buttons: [Cancel] (left / secondary), [Send] (right / primary).
- Default focus: Cancel.

**States.**

- **Visible:** shows the dialog with actual file count and size.
- **Dismissed (Cancel):** dialog closes, user returns to the picker / DeviceListScreen with no pending state; selection is cleared.
- **Confirmed (Send):** dialog closes, transfer begins, navigates to SendProgressScreen.

**Interactions.**

- Tap [Cancel]: dismiss dialog, clear selection, return to DeviceListScreen.
- Tap [Send]: close dialog, proceed to SendProgressScreen.
- Hardware back / Escape key: same as [Cancel].

**Copy.**

- "Large folder"
- "About to send \<N\> files (\<size\>) to \<peer\>. Continue?"
- "Cancel"
- "Send"

**Per-platform deltas.**

- Android: system-style dialog. Hardware back = Cancel.
- iOS: UIAlertController-style presentation. Swipe-to-dismiss blocked (destructive intent — default on Cancel keeps Cancel as default).
- macOS: standard sheet attached to the app window.
- Desktop JVM: standard modal dialog.

**Accessibility.**

- Dialog role: `alertdialog`.
- On open, focus is placed on [Cancel].
- [Cancel] semantic label: "Cancel — discard folder selection".
- [Send] semantic label: "Send \<N\> files to \<peer\>".
- Escape key always triggers Cancel on Desktop/macOS.

---

### SendProgressScreen

**Purpose.** The sender's primary screen during an active transfer, showing batch progress via the `•—•` brand mark and live speed.

**Entry points.** Immediately after pairing (or silently if already paired) when the receiver accepts the connection. Replaces the DeviceListScreen on the navigation stack (sender navigates here; back is suppressed during transfer). On iOS, this screen is also the anchor for the foreground constraint banner.

**Layout.**

- Top bar: peer name as title; [Cancel] affordance in the trailing position.
- Center region: the `•—•` brand mark in transfer-progress state — the connecting line fills accent color left-to-right proportional to total bytes transferred in the batch. The mark is the only progress bar; no secondary bar.
- Below the mark: current filename (one line, center-truncated with ellipsis in the middle when too long).
- Below filename: current speed (e.g. "3.2 MB/s").
- Skip count badge (appears only when at least one file has been skipped): muted-tone secondary badge near the filename: "2 files skipped".

**States.**

- **Preparing (connection established, enumeration in progress):** `•—•` in transfer-progress state at 0%; below the mark: "Preparing…"; speed field: empty.
- **In-progress:** `•—•` filling left-to-right; filename shows current file being sent; speed shows "Calculating..." for the first ~3 s, then live value (e.g. "3.2 MB/s"). Skip badge appears if any files are skipped so far.
- **Completed (all files sent, no failures):** `•—•` in success state (right dot animation fires); screen transitions automatically to TransferSummaryScreen after the ~700 ms success animation completes.
- **Completed (partial failures):** same success animation, then transitions to TransferSummaryScreen showing the partial-failure summary.
- **All failed:** `•—•` in error state (line truncated at failure point, right dot hollow in error tone); screen transitions to TransferSummaryScreen.
- **Connection lost mid-transfer:** `•—•` in error state; overlay or inline message below mark: "Connection lost. Try again when you're back on Wi-Fi."; [Retry] button visible; [Done] secondary.
- **Peer unreachable mid-transfer:** `•—•` in error state; message: "\<peer\> is no longer reachable. Try again."; [Retry] and [Done].
- **Cancelled (by sender):** brief neutral-tone "Cancelled" message on this screen, then returns to DeviceListScreen (no TransferSummaryScreen).
- **Cancelled (by receiver):** same — sender's screen shows neutral "Cancelled", returns to DeviceListScreen.

**Interactions.**

- Tap [Cancel]: immediately cancels transfer; no confirm dialog. Both sides stop. Screen shows "Cancelled" briefly then returns to DeviceListScreen.
- Tap [Retry] (error state): re-initiates the same transfer (all files or failed files depending on the error type) to the same peer if peer is reachable.
- Tap [Done] (error state): returns to DeviceListScreen without retry.
- Hardware back (Android) / swipe-back gesture (iOS): suppressed during active transfer (prevents accidental cancel). If transfer is in an error state, back is permitted (equivalent to [Done]).

**Copy.**

- "Sending to \<peer\>" (top bar title)
- "Cancel"
- "Preparing…"
- "Calculating..."
- "3.2 MB/s" (example — live value)
- "\<filename\>" (center-truncated)
- "\<N\> files skipped"
- "Connection lost. Try again when you're back on Wi-Fi."
- "\<peer\> is no longer reachable. Try again."
- "Retry"
- "Done"
- "Cancelled"

**Per-platform deltas.**

- Android: [Cancel] in top bar trailing slot. Hardware back suppressed during active transfer via back-press handler; permitted in error state. FGS runs in background so no foreground constraint banner on this screen.
- iOS: [Cancel] in top bar trailing slot. Swipe-back gesture suppressed during active transfer. Foreground constraint banner rendered persistently at the top of this screen (above the top bar or as a pinned system-style banner): "Keep Tether open to complete the transfer."
- macOS: [Cancel] in toolbar. Window close button should warn if a transfer is active: a sheet appears — "A transfer is in progress. Cancel it and close?" [Keep Sending] [Cancel Transfer]. This is the only platform with a window-close scenario.
- Desktop JVM: same as macOS for window-close warning.

**Accessibility.**

- `•—•` mark carries `contentDescription` that updates at the three defined live-region announcement points only (not byte-by-byte):
  - Start: "Sending \<N\> files to \<peer\>"
  - Per-file failure: "Failed to send \<filename\>"
  - Done: "Sent \<N\> files" or "Sent \<X\> of \<Y\> files, \<Z\> failed"
- Live-region role: `polite` except for failure announcements which are `assertive`.
- [Cancel] semantic label: "Cancel transfer to \<peer\>".
- Speed label semantic: "Transfer speed: \<value\>".
- Filename label semantic: "Currently sending: \<filename\>".
- Skip-count badge semantic: "\<N\> files skipped so far".
- [Retry] semantic label: "Retry sending to \<peer\>".
- Keyboard focus order (Desktop/macOS): top bar → [Cancel] → content area (read-only) → [Retry] / [Done] in error state.

---

### IncomingTransferCard

**Purpose.** An inline card rendered on the receiver's DeviceListScreen during an inbound transfer, showing the sender's identity and batch progress. Not a separate screen — it sits above the device list rows.

**Entry points.** Rendered automatically when an inbound transfer begins from a paired peer. Dismissed automatically on completion (after 4-second success state) or on cancel.

**Layout.**

- Card region (stacked above device rows, one card per concurrent sender):
  - Sender name (e.g. "From Artem's Phone")
  - Subtitle: "Receiving \<N\> files (\<total size\>)"
  - `•—•` brand mark in transfer-progress state (line fills left-to-right as bytes arrive)
  - [Cancel] affordance in trailing position on the card

**States.**

- **Receiving (in-progress):** card visible; `•—•` filling; subtitle shows file count and total size.
- **Success (4-second hold):** `•—•` transitions to success state; card background shifts to success tone; copy changes to "Received \<N\> files from \<peer\> — tap to open". After 4 seconds, card self-dismisses.
- **Cancelled (by receiver or sender):** card disappears immediately; a brief neutral-tone toast appears: "Transfer from \<peer\> cancelled".
- **Error / connection lost:** card switches to error state; `•—•` in error state; copy: "Transfer from \<peer\> interrupted. Ask \<peer\> to retry."; card persists until dismissed by the user (no auto-dismiss on error — user needs to read it).
- **iOS suspension / screen lock interruption:** when the OS suspends Tether mid-transfer on iOS, on next foreground a one-time informational card appears (distinct from an active card): "Transfer from \<peer\> was interrupted. Ask \<peer\> to send again." with a [Dismiss] affordance.
- **Two concurrent inbound transfers:** two stacked cards, each independent, with their own `•—•` and [Cancel].

**Interactions.**

- Tap [Cancel] on card: immediately cancels that inbound transfer. No confirm dialog. Both sides stop. Card dismisses. Brief toast: "Transfer from \<peer\> cancelled".
- Tap card in success state ("Received \<N\> files from \<peer\> — tap to open"): triggers the platform's reveal behavior (see per-platform deltas).
- Tap iOS suspension card [Dismiss]: dismisses the informational card.

**Copy.**

- "From \<peer\>"
- "Receiving \<N\> files (\<size\>)"
- "Cancel"
- "Received \<N\> files from \<peer\> — tap to open"
- "Transfer from \<peer\> cancelled"
- "Transfer from \<peer\> interrupted. Ask \<peer\> to send again." (iOS suspension)
- "Dismiss"

**Per-platform deltas.**

- Android: tap success card → opens system Files app at `Downloads/Tether/`. OS completion notification also fires; tapping notification has the same effect.
- iOS: tap success card → opens ReceivedScreen (in-app). OS completion notification fires when possible; tapping notification also opens ReceivedScreen. iOS suspension card (see iOS interruption state above) is iOS-only.
- macOS: tap success card → Tether activates and reveals and selects the received file in Finder. OS notification also fires with the same effect.
- Desktop JVM: tap success card → opens File Explorer (Windows) or best-effort file manager (Linux) at the parent folder. OS notification fires with the same effect. Linux reveal may silently no-op in some DEs — the card tap is the user's primary affordance.

**Accessibility.**

- Each card is a live region (polite); announces once on arrival: "Receiving \<N\> files from \<peer\>".
- [Cancel] semantic label: "Cancel incoming transfer from \<peer\>".
- Success state card: role changes to button; semantic label: "Received \<N\> files from \<peer\>. Tap to open."; announces once when state changes (assertive, once only).
- Error/interruption state: assertive announcement: "Transfer from \<peer\> interrupted."
- Two stacked cards: each card is independently focusable.

---

### TransferSummaryScreen

**Purpose.** The end-of-batch result screen shown after every completed send (on the sender's side), covering all-success, partial-failure, and all-failed outcomes.

**Entry points.** Automatically after SendProgressScreen completes (whether fully successful, partial-failure, or all-failed). Not reachable directly.

**Layout.**

- Top bar: "Transfer complete" title; back/close affordance (returns to DeviceListScreen).
- `•—•` in success state (all success) or error state (any failures).
- Primary summary line (large text).
- Secondary detail (expandable for failure list).
- [Retry failed files] button (visible only when there are failed files AND the peer is still reachable).
- [Done] button (always visible — returns to DeviceListScreen).

**States.**

- **All success:** `•—•` in success state. Primary: "Sent \<N\> files to \<peer\>." No retry button. [Done] button.
- **Partial failure (collapsed):** `•—•` in error state. Primary: "\<X\> of \<Y\> files sent. \<K\> failed." Secondary standalone button: [Show details]. [Retry failed files] button (enabled if peer is still reachable; disabled with helper text "\<peer\> is no longer reachable" if peer has left mDNS). [Done] button.
- **Partial failure (expanded):** same as collapsed but [Show details] becomes [Hide details]; a scrollable list of failed filenames appears below the primary line.
- **All failed:** `•—•` in error state. Primary: "0 of \<Y\> files sent. All failed." [Show details] / [Hide details] and same retry/done button behavior.
- **Retry in progress:** [Retry failed files] transitions to a loading state ("Retrying…"); the screen effectively becomes SendProgressScreen again for the failed-files subset.
- **Peer gone (retry unavailable):** [Retry failed files] is visible but disabled. Helper text below: "\<peer\> is no longer reachable."

**Interactions.**

- Tap [Show details]: expands the failed-file list in place (no new screen); button label changes to [Hide details]. Tapping [Hide details] collapses the list again.
- Tap [Retry failed files] (enabled): re-initiates a new transfer session to the same peer for only the failed files. Navigates back to SendProgressScreen.
- Tap [Retry failed files] (disabled): no action (button is non-interactive with helper text).
- Tap [Done] or back affordance: returns to DeviceListScreen.
- Hardware back (Android) / swipe-back (iOS): same as [Done].

**Copy.**

- "Transfer complete"
- "Sent \<N\> files to \<peer\>."
- "\<X\> of \<Y\> files sent. \<K\> failed."
- "Show details"
- "Hide details"
- "Retry failed files"
- "Retrying…"
- "\<peer\> is no longer reachable."
- "Done"

**Per-platform deltas.**

- Android: hardware back returns to DeviceListScreen (same as [Done]).
- iOS: swipe-back returns to DeviceListScreen. Top bar shows a standard back chevron.
- macOS: top bar shows close affordance (×). Window is not closed — returns to DeviceListScreen within the same window.
- Desktop JVM: same as macOS.

**Accessibility.**

- `•—•` mark `contentDescription`: "Transfer complete" (success) or "Transfer completed with failures" (any failures).
- [Show details] / [Hide details] is a button; semantic label matches the visible label exactly ("Show details" / "Hide details") depending on state.
- [Retry failed files] semantic label when disabled: "Retry not available — \<peer\> is no longer reachable."
- Failed file list: each filename is a read-only text element, not interactive.
- Live-region announcement on screen entry (assertive): the primary summary line is announced once.
- Keyboard focus order (Desktop/macOS): back/close → summary text → expand/collapse button → [Retry failed files] (if present) → [Done].

---

### AutoPickInlinePrompt

**Purpose.** A one-time inline prompt at the top of the device list that lets the user opt into auto-sending to a single online paired peer, surfaced the first time the condition is met naturally.

**Entry points.** Rendered automatically at the top of the DeviceListScreen (between the pending/active banner and the device rows) the **first time** all three conditions hold simultaneously: a pending outbound exists (share-sheet arrival, drag-and-drop arrival, or the user picked files in-app and is now on the device list with an active pending-banner), exactly one online paired peer is currently visible, and the prompt has never been shown before. Shown once ever (until the user dismisses it or enables the toggle). The prompt does not appear when the user opens Tether with no pending outbound.

**Layout.**

- A dismissible strip / inline card below the pending/active banner (or at the top of the list if no banner is active).
- Body: "Always send to \<peer\> when it's your only online device?"
- Toggle control (default Off) on the right side of the strip.
- [×] dismiss affordance in the trailing corner.

**States.**

- **Visible (toggle Off — default):** strip shows with toggle set to Off.
- **Toggle flipped On:** AutoPickConfirmDialog fires immediately.
- **Dismissed (via [×]):** strip disappears permanently (never shown again). Toggle remains Off. Settings is the only path to enable.
- **Permanently gone (after toggle saved as On or Off via dialog):** strip never appears again.

**Interactions.**

- Flip toggle to On: triggers AutoPickConfirmDialog.
- Tap [×]: dismisses strip permanently. No toast or confirmation.
- Tap anywhere else on strip (not toggle, not ×): no action (strip is not a button — only the toggle and × are interactive).

**Copy.**

- "Always send to \<peer\> when it's your only online device?"
- [×] semantic label: "Dismiss auto-send prompt"

**Per-platform deltas.**

- Android: default.
- iOS: default.
- macOS: default (strip at top of device list).
- Desktop JVM: default.

**Accessibility.**

- Strip is not a live region — it is persistent and the user will notice it visually when scrolling.
- Toggle semantic label: "Auto-send to \<peer\> when it's the only online device, currently Off."
- [×] semantic label: "Dismiss auto-send prompt".
- Focus order within strip: toggle → [×].

---

### AutoPickConfirmDialog

**Purpose.** A confirmation dialog shown when the user flips the auto-send toggle to On in AutoPickInlinePrompt, confirming the preference and immediately triggering a send.

**Entry points.** Triggered by flipping the toggle in AutoPickInlinePrompt.

**Layout.**

- Modal dialog.
- Title: "Auto-send enabled"
- Body: "You can turn this off in Settings. Sending to \<peer\> now."
- Two buttons: [Cancel] (secondary), [OK] (primary).
- Default focus: OK.

**States.**

- **Visible:** dialog shown after toggle flip.
- **Confirmed ([OK]):** preference saved to On in Settings; send initiates immediately to the one online peer; dialog closes; AutoPickInlinePrompt strip disappears; DeviceListScreen shows the pending/active outbound banner.
- **Cancelled ([Cancel]):** toggle reverts to Off; no send initiated; dialog closes; AutoPickInlinePrompt strip remains (toggle shows Off again).

**Interactions.**

- Tap [OK]: save preference, initiate send, close dialog.
- Tap [Cancel]: revert toggle, close dialog.
- Hardware back / Escape: same as [Cancel].

**Copy.**

- "Auto-send enabled"
- "You can turn this off in Settings. Sending to \<peer\> now."
- "Cancel"
- "OK"

**Per-platform deltas.**

- Android: default; hardware back = Cancel.
- iOS: UIAlertController style.
- macOS: standard alert sheet.
- Desktop JVM: standard modal dialog; Escape = Cancel.

**Accessibility.**

- Dialog role: `alertdialog`.
- Default focus: [OK].
- [Cancel] semantic label: "Cancel — keep auto-send off."
- [OK] semantic label: "Enable auto-send and send to \<peer\> now."

---

### ReceivedScreen (iOS only)

**Purpose.** An in-app screen on iOS that confirms a completed inbound transfer and helps the user navigate to the received files, substituting for a direct Files deep-link.

**Entry points.** Tapping an OS completion notification on iOS (which opens Tether). Also reachable by tapping the success state of IncomingTransferCard before it self-dismisses.

**Layout.**

- Top bar: "Files received" title; [Done] affordance (returns to DeviceListScreen).
- `•—•` in success state.
- Summary: "Received \<N\> files from \<peer\>."
- Path display (read-only): "Saved to: On My iPhone → Tether/"
- Primary button: [Show in Files] — attempts an iOS system deep-link to open the Files app at the Tether folder.
- Fallback state (if the deep-link fails): [Show in Files] is replaced by a visible inline note: "Open Files → On My iPhone → Tether" (static instruction text, not a button).

**States.**

- **Default:** summary + path + [Show in Files] button.
- **Deep-link failure:** [Show in Files] button replaced by inline instruction: "Open Files → On My iPhone → Tether".
- **Done:** returns to DeviceListScreen.

**Interactions.**

- Tap [Show in Files]: attempts a system deep-link to open the Files app at the Tether folder. If the deep-link fails, transitions to the inline fallback state (the button disappears, the instruction text "Open Files → On My iPhone → Tether" appears in its place).
- Tap [Done] or back: returns to DeviceListScreen.

**Copy.**

- "Files received"
- "Received \<N\> files from \<peer\>."
- "Saved to: On My iPhone → Tether/"
- "Show in Files"
- "Open Files → On My iPhone → Tether"
- "Done"

**Per-platform deltas.**

- iOS only — this screen does not exist on Android, macOS, or Desktop.

**Accessibility.**

- `•—•` mark `contentDescription`: "Transfer complete".
- [Show in Files] semantic label: "Open Files app at Tether folder".
- Inline fallback instruction: role is static text; not a button.
- [Done] semantic label: "Done — return to device list".

---

### SettingsSection — File Transfer

**Purpose.** The settings area where the user configures save location and the auto-send toggle.

**Entry points.** App settings (existing settings surface) — a dedicated "File Transfer" section within it.

**Layout.**

- Section header: "File Transfer"
- Save location row: label "Save location"; value shows the current path (e.g. "Downloads/Tether/"); on editable platforms, a disclosure affordance (chevron or "Change" link) opens the system folder picker.
- Auto-send toggle row: label "Auto-send when only one device is online"; description: "Skip the device list and send immediately when only one of your paired devices is online."; toggle control (On/Off).

**States.**

- **Android (editable):** save location shows current path; tap row → system folder picker to change.
- **iOS (read-only):** save location shows "On My iPhone → Tether/"; no change affordance. A "(fixed)" caption beneath the path: "iOS does not allow changing this location."
- **macOS (editable):** save location shows current path (default: "~/Downloads/Tether/"); disclosure affordance → system folder picker.
- **Desktop JVM (editable):** same as macOS.
- **Auto-send toggle On:** when enabled, a helper text appears below the row: "Active — will send automatically to \<peer name\> when it's your only online device." (If no peer has been designated yet, helper text: "Will activate when exactly one paired device is online.")
- **Auto-send toggle Off:** no helper text.

**Interactions.**

- Tap save location row (editable platforms): opens system folder picker. On confirmation, path updates in the row.
- Toggle auto-send: flips the preference immediately (no confirm dialog from settings — the dialog only fires from the inline prompt on first enable). When toggled On from Settings and there is no active mDNS peer context, no dialog fires; preference is saved silently.
- Tap save location row (iOS): no action (row is non-interactive; tap produces no response).

**Copy.**

- "File Transfer"
- "Save location"
- "Downloads/Tether/" (or platform-specific default)
- "On My iPhone → Tether/" (iOS)
- "iOS does not allow changing this location."
- "Auto-send when only one device is online"
- "Skip the device list and send immediately when only one of your paired devices is online."
- "Active — will send automatically to \<peer name\> when it's your only online device."
- "Will activate when exactly one paired device is online."

**Per-platform deltas.**

- Android: save location editable via system folder picker.
- iOS: save location read-only with explanatory caption.
- macOS: save location editable via system open-panel (folder selection).
- Desktop JVM: save location editable via system folder picker (AWT or equivalent).

**Accessibility.**

- Save location row (editable): semantic label "Change save location, currently \<path\>".
- Save location row (iOS, read-only): semantic label "Save location: On My iPhone → Tether/. This location cannot be changed on iOS."
- Auto-send toggle: semantic label "Auto-send when only one device is online, currently \<On/Off\>."
- Helper text below toggle: announced as static text, not a live region.

---

## Flows

### Flow 1 — In-app send, N files, already paired

1. User opens Tether → DeviceListScreen in populated state.
2. User taps target peer's device row.
3. **Android/iOS:** MobilePickerChooserSheet appears. User taps "Photos" or "Files". System picker opens. User selects files. Sheet closes.
   **macOS/Desktop:** System file dialog opens directly. User selects files.
4. If selection is a folder exceeding the soft threshold, FolderSendConfirmDialog appears. User taps [Send].
5. Pairing handshake happens silently (already paired). SendProgressScreen appears.
6. `•—•` fills left-to-right. Filename and speed update live. Receiver's DeviceListScreen shows IncomingTransferCard.
7. Transfer completes. `•—•` plays success animation. Screen transitions to TransferSummaryScreen ("Sent \<N\> files to \<peer\>.").
8. User taps [Done] → DeviceListScreen.

**Receiver side:**
- IncomingTransferCard success state holds for 4 seconds ("Received \<N\> files from \<peer\> — tap to open"), then self-dismisses.
- OS notification fires. Tapping opens the saved location (platform-appropriate).

### Flow 2 — Share-sheet entry, already paired

1. User is in Photos / Files app. Taps Share → Tether.
2. Tether opens at DeviceListScreen with pending-outbound banner: "Ready to send \<N\> files (\<size\>). Pick a device below."
3. User taps target peer's row.
4. If folder threshold exceeded: FolderSendConfirmDialog. Otherwise: proceeds directly.
5. SendProgressScreen, then TransferSummaryScreen — same as Flow 1 from step 5.

### Flow 3 — Auto-send toggle ON, one peer online (canonical: share-sheet entry)

1. User taps Share → Tether (or drags files onto the Tether window on macOS/Desktop) with auto-send toggle On and exactly one paired peer online.
2. DeviceListScreen is skipped. Pending-outbound banner appears immediately: "Sending to \<peer\>" with [Cancel].
3. Transfer begins without user picking a peer.
4. SendProgressScreen, then TransferSummaryScreen.

Note: auto-send also fires when the user picks files in-app (via MobilePickerChooserSheet or system file dialog) and returns to DeviceListScreen with a pending outbound — if exactly one peer is online and the toggle is On, the send begins immediately without the user tapping a device row.

If the user taps [Cancel] in the banner before the transfer progresses: send is cancelled, banner transitions to "Cancelled" toast, then dismisses.

### Flow 4 — First-time auto-send toggle discovery

1. User arrives at DeviceListScreen with a pending outbound (share-sheet arrival, drag-and-drop, or files picked in-app) and exactly one paired peer is online. AutoPickInlinePrompt strip appears at top of DeviceListScreen (first time ever all three conditions are met simultaneously).
2. User sees "Always send to \<peer\> when it's your only online device?" — toggle is Off.
3. User flips toggle to On → AutoPickConfirmDialog fires.
4. User taps [OK]: preference saved, send immediately begins to that peer (same as Flow 3 from step 3 onward). Strip disappears permanently.
5. Alternatively, user taps [Cancel] in dialog: toggle reverts to Off, strip remains visible with toggle Off.
6. Alternatively, user taps [×] on strip: strip dismissed permanently, toggle stays Off, no send.

### Flow 5 — Partial batch failure and retry

1. Transfer completes with some file failures (per-file errors during send).
2. SendProgressScreen transitions to TransferSummaryScreen.
3. TransferSummaryScreen shows: "\<X\> of \<Y\> files sent. \<K\> failed." with [Show details] button.
4. User taps [Show details] → list of failed filenames expands inline; button becomes [Hide details].
5. User taps [Retry failed files] (peer still online) → SendProgressScreen re-appears for the failed-file subset.
6. If retry succeeds: TransferSummaryScreen shows "Sent \<K\> files to \<peer\>." (only the retried batch).
7. If peer went offline: [Retry failed files] is disabled; helper text "\<peer\> is no longer reachable" shown below button.

### Flow 6 — Cancel mid-transfer (sender)

1. Transfer is in progress on SendProgressScreen.
2. User taps [Cancel].
3. Both sides stop immediately. No confirm dialog.
4. SendProgressScreen shows brief "Cancelled" then returns to DeviceListScreen.
5. Receiver's IncomingTransferCard dismisses with toast: "Transfer from \<peer\> cancelled".

### Flow 7 — Cancel mid-transfer (receiver)

1. Inbound transfer is in progress. IncomingTransferCard visible on receiver's DeviceListScreen.
2. Receiver taps [Cancel] on the card.
3. Both sides stop immediately.
4. Card dismisses with toast: "Transfer from \<peer\> cancelled".
5. Sender's SendProgressScreen shows neutral "Cancelled", returns to DeviceListScreen.

### Flow 8 — Connection lost mid-transfer

1. Transfer is in progress. Wi-Fi drops or peer becomes unreachable.
2. SendProgressScreen switches to error state: `•—•` in error state.
3. If Wi-Fi lost: "Connection lost. Try again when you're back on Wi-Fi." [Retry] [Done].
4. If peer gone: "\<peer\> is no longer reachable. Try again." [Retry] [Done].
5. User taps [Retry]: re-initiates transfer to the same peer (if peer is back on mDNS). Returns to in-progress state.
6. User taps [Done]: returns to DeviceListScreen.

### Flow 9 — iOS foreground suspension during inbound transfer

1. Inbound transfer is in progress on receiver's iOS device.
2. User locks screen or OS suspends Tether.
3. Transfer dies. No completion notification (app is not running to fire it). Partial file discarded.
4. User brings Tether to foreground.
5. IncomingTransferCard shows iOS-suspension state: "Transfer from \<peer\> was interrupted. Ask \<peer\> to send again." [Dismiss].
6. Sender simultaneously sees "\<peer\> is no longer reachable. Try again." on SendProgressScreen.

### Flow 10 — Drag-and-drop onto Tether window (macOS / Desktop)

1. User drags file(s) from Finder / File Explorer onto the Tether window.
2. DeviceListScreen shows pending-outbound banner: "Ready to send \<N\> files (\<size\>). Pick a device below."
3. User taps / clicks target peer row.
4. No picker sheet — files already selected. Proceeds directly to FolderSendConfirmDialog (if threshold exceeded) or SendProgressScreen.

---

## Navigation

**DeviceListScreen** is the root screen. It is never replaced — it is always beneath any other screen in the stack.

**MobilePickerChooserSheet** is a bottom-sheet modal overlaid on DeviceListScreen (Android/iOS only). Dismissing it returns focus to DeviceListScreen without navigating anywhere.

**FolderSendConfirmDialog** is a modal dialog. It can appear over DeviceListScreen (if triggered from the picker sheet or from a drag-drop) or over SendProgressScreen if folder selection happened late in a flow. Dismissing returns to the triggering context.

**SendProgressScreen** is pushed onto the stack (replaces DeviceListScreen in the visual stack on mobile; on Desktop/macOS it fills the main content area). Back navigation is suppressed during active transfer. In error state, back is restored.

**TransferSummaryScreen** is pushed after SendProgressScreen (replaces it). [Done] or back returns to DeviceListScreen (pops back to root, not to SendProgressScreen). The "Retry failed files" path pushes a new SendProgressScreen instance.

**AutoPickConfirmDialog** is a modal dialog overlaid on DeviceListScreen. It does not add to the navigation stack.

**ReceivedScreen (iOS only)** is pushed modally from the app delegate when a notification tap routes into Tether. [Done] pops it back to DeviceListScreen.

**SettingsSection — File Transfer** lives within the existing settings navigation surface (pushed from the top-bar settings affordance on DeviceListScreen). It does not introduce a new navigation root.

---

## Conceptual components

1. **Pending/active transfer banner** — non-dismissible strip above device rows; two variants (pending and active); hosts a [Cancel] affordance and a 4-second success/cancelled toast transition.
2. **Incoming transfer card** — inline card rendered above device rows on the receiver; contains sender identity, `•—•` in transfer-progress state, subtitle, and [Cancel]; transitions to success state ("Received \<N\> files from \<peer\> — tap to open") with tap-to-open; self-dismisses after 4 seconds on success.
3. **Transfer progress mark** — the `•—•` brand mark in transfer-progress state (line fills left-to-right proportional to bytes). Used on SendProgressScreen and IncomingTransferCard.
4. **Transfer success mark** — the `•—•` in success state (right-dot animation fires). Used on SendProgressScreen, TransferSummaryScreen, ReceivedScreen.
5. **Transfer error mark** — the `•—•` in error state (line truncated, right dot hollow in error tone). Used on SendProgressScreen and TransferSummaryScreen.
6. **Current-file label** — one-line center-truncated filename display. Used on SendProgressScreen below the brand mark.
7. **Transfer speed label** — live speed readout with "Calculating..." initial state. Used on SendProgressScreen.
8. **Skip-count badge** — muted-tone secondary badge showing running file-skip count. Used on SendProgressScreen.
9. **Picker chooser sheet (mobile)** — bottom sheet with three tappable source options (Photos / Files / Folder). Android + iOS only.
10. **Folder-send confirm dialog** — destructive-default modal dialog with real file count and size; default focus on Cancel.
11. **Transfer summary panel** — end-of-batch result display with expandable failure list and retry affordance.
12. **Retry-failed-files button** — primary button that is disabled (with peer-gone helper text) when the peer has left mDNS.
13. **Auto-pick inline prompt strip** — one-time dismissible strip with a toggle and [×] affordance; lives at the top of the device list.
14. **Auto-pick confirm dialog** — modal confirmation when auto-send is enabled; default focus OK; immediate send on confirm.
15. **iOS foreground constraint banner** — persistent non-dismissible system-style banner informing the user to keep Tether open during transfers; iOS only.
16. **iOS received screen** — in-app completion screen with "Show in Files" deep-link affordance and inline fallback instruction; iOS only.
17. **Settings save-location row** — editable (with system folder picker disclosure) on Android/macOS/Desktop; read-only with explanatory caption on iOS.
18. **Settings auto-send toggle row** — toggle with contextual helper text describing the current auto-send target peer.
19. **macOS window-close transfer warning** — sheet attached to the window when the user closes Tether mid-transfer; macOS and Desktop only.

---

## Open UX questions

These are non-blocking future considerations. None gate the current implementation.

1. **Receiver-side retry for batch failures.** Sender-side retry is in scope for this feature; receiver-initiated retry is deferred. A receiver-side retry — where the receiver itself requests the sender to re-send only the failed files after freeing space — requires a pull-protocol shape, a decision on how long the failed-batch reference persists, and whether the sender must still be online. Deferred until the sender-side retry ships and usage signals the demand.

2. **Mobile picker unification — the "two taps" gap.** The MobilePickerChooserSheet adds one tap to the vision's "two taps to send" on Android and iOS, caused by OS constraints (SAF and UIDocumentPickerViewController do not support mixing folder + multi-file selection in a single picker session). Revisit once OS support evolves or an in-app picker becomes viable.

3. **iOS deep-link to Files app reliability.** The fallback toast on ReceivedScreen ("Open Files → On My iPhone → Tether") diverges from the non-technical-user principle in audience.md. Monitor real failure rate post-launch; if the deep-link proves unreliable, a more guided in-app flow or a different reveal mechanism may be needed.
