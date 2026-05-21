# UX brief — File transfer

**Spec:** [spec.md](spec.md)
**Status:** `ready`

---

## Information architecture

This feature introduces four new screens / dialogs plus one settings section, touches one existing screen (DeviceListScreen). All per-peer transfer state — including in-progress transfers — is contained within PeerCard. The card is the sole transfer surface; there is no separate full-screen progress view.

```
DeviceListScreen  (existing — touched)
├── [banner: pending outbound]         ← persistent, above peer-cards
├── [banner: iOS foreground constraint]← iOS only, persistent during transfer
└── scrollable list of PeerCards       ← each card is an independent state machine
      │
      │  PeerCard states:
      │    Idle (collapsed / expanded)
      │    Active outbound (sending)
      │    Active inbound (receiving)
      │    Connection paused / reconnecting
      │    Received (inbound complete)
      │    Sent (outbound complete)
      │    Error
      │    Cancelled
      │
      ├── [tap peer, idle, pending outbound exists] ─────────────┐
      │                                                           ▼
      │                                         LargeSelectionConfirmDialog
      │                                                           │
      │                                           (PeerCard → Active outbound)
      │
      └── [tap peer, idle, no pending outbound] ─────────────────┐
                                                                  ▼
                                          MobilePickerChooserSheet (Android + iOS only)
                                                                  │
                                                     [large selection?] ─── LargeSelectionConfirmDialog
                                                                  │
                                                  (PeerCard → Active outbound)
      │
      └── PeerCard in any non-Idle state (Active outbound / Active inbound /
              Sent / Received / Cancelled / Error)
                                                                  │
                                                       [Show details →]
                                                                  │
                                                    TransferDetailsScreen

SettingsSection — File Transfer  (inside the app's existing settings surface)
```

Screens introduced: LargeSelectionConfirmDialog, TransferDetailsScreen, MobilePickerChooserSheet, SettingsSection — File Transfer.

Screens touched: DeviceListScreen (peer rows replaced by PeerCards with full state machine).

---

## Screens

### DeviceListScreen

**Purpose.** The home screen where the user sees online peers (as PeerCards) and manages any in-flight or pending transfer state.

**Entry points.** App launch; OS share-sheet / "Open with" routing Tether to foreground with files pre-selected; drag-and-drop files onto the Tether window (macOS/Desktop).

**Layout.**

- iOS foreground constraint banner (iOS only, present during any active transfer): persistent, non-dismissible, at very top. Copy: "Keep Tether open to complete the transfer."
- Pending-outbound banner (present only when files are queued but no peer chosen yet): renders below the iOS constraint banner (if shown) and above the peer-card list. Copy: "Ready to send \<N\> files (\<size\>). Pick a device below." with [Cancel button] on the right. No self-dismiss.
- Scrollable list of PeerCards, each independently stateful.

**States.**

- **Searching (no peers yet, no pending transfer):** the `•—•` brand mark in its searching state (hollow right dot, opacity oscillation); copy: "Searching for devices…". No pending-outbound banner.
- **Searching (with pending outbound):** same searching animation; pending-outbound banner above reads "Ready to send \<N\> files (\<size\>). Pick a device below." with [Cancel button].
- **Populated (no pending transfer):** peer-cards visible, each in Idle (collapsed) state unless otherwise.
- **Populated (pending outbound):** peer-cards visible; pending-outbound banner shows. Tapping any peer-card body transitions that card to Active outbound and dismisses the banner.
- **iOS foreground constraint:** an additional persistent banner at top (iOS only) during any active transfer. Disappears automatically when no transfer is active.

**Interactions.**

- Tap pending-outbound banner [Cancel button]: clears pending selection, dismisses the banner. No confirm dialog.
- Tap PeerCard body (idle, pending outbound exists): initiates send to that peer. On any platform: first shows LargeSelectionConfirmDialog if threshold exceeded, then transitions card to Active outbound.
- Tap PeerCard body (idle, no pending outbound): on Android/iOS, opens MobilePickerChooserSheet. On macOS/Desktop, opens system file dialog.
- Tap PeerCard chevron (idle): expands or collapses that card's inline settings block.
- All other PeerCard interactions: handled within the card itself (see PeerCard below).

**Copy.**

- "Searching for devices…"
- "Ready to send \<N\> files (\<size\>). Pick a device below."
- "Keep Tether open to complete the transfer." (iOS only)

**Per-platform deltas.**

- Android: default. Share-sheet entry sets pending state and shows banner.
- iOS: adds persistent iOS foreground constraint banner during any transfer. Share-sheet entry sets pending state.
- macOS: drag-and-drop of files onto the Tether window sets pending state and shows the banner. System file dialog replaces MobilePickerChooserSheet. No foreground constraint banner.
- Desktop JVM: same as macOS for drag-and-drop and file dialog. No share-sheet. No foreground constraint banner.

**Accessibility.**

- Pending-outbound banner is a live region (assertive); announces once when it first appears and when content changes.
- [Cancel button] semantic label: "Cancel pending transfer".
- iOS foreground constraint banner: role is `alert`; announced once when it appears.
- Searching state `•—•` mark `contentDescription`: "Searching for devices".
- PeerCard list: each card is independently focusable (see PeerCard accessibility).

---

### PeerCard

**Purpose.** The single point of interaction with a remote peer — covers idle browsing, per-peer settings, all transfer progress (both outbound and inbound), and all post-transfer outcomes. It is not a separate screen; it renders inline within DeviceListScreen's scrollable list.

**Entry points.** Always present on DeviceListScreen for each known peer. State transitions happen in response to user actions and network events.

**Layout (state-dependent — see States below).** In every state the card carries the peer name and current status. The card "swells" (grows in height) when a transfer is active or when expanded.

**States.**

#### 1. Idle (collapsed)

The card's resting state.

- Peer name (primary label).
- Status indicator: "Online" / "Paired — offline" (muted).
- Trailing: chevron `▾` (expand affordance, not a button label — icon only).

No transfer-related content visible.

#### 2. Idle (expanded)

The card expanded to reveal per-peer settings.

- Same idle row at top; chevron rotates to `▴`.
- Inline block beneath the row:
  - Per-peer auto-send toggle: label "Auto-send to this device when it's your only online device"; description "Sends immediately — no device-list tap required." Toggle control (On/Off).
  - [i] info icon button beside the label: tap → tooltip (or popover on Desktop/macOS): "Tether will skip the device list and send straight to \<peer\> when no other paired devices are online."

#### 3. Active outbound (sending)

Card swells. Transfer in progress from this device to the peer.

- Peer name (top row).
- `•—•` brand mark in transfer-progress state (line fills left-to-right, proportional to total bytes transferred).
- Current filename (one line, center-truncated with ellipsis).
- Progress copy: "X.X MB of Y.Y MB".
- Transfer speed: "3.2 MB/s" (shows "Calculating…" for first ~3 s).
- Both progress copy and speed are always shown — not one or the other.
- Skip-count badge (appears only when ≥1 file skipped): muted-tone secondary badge: "\<N\> files skipped".
- A [Show details →] button opens TransferDetailsScreen in its in-progress mode (live view of files-received-so-far). Optional drill-down for large batches; the card remains the primary surface.
- [Cancel button] in the trailing position (explicitly a button, visually distinct, labeled "Cancel").

#### 4. Active inbound (receiving)

Symmetric to Active outbound. Card swells.

- Peer name (top row, prefixed: "From \<peer\>").
- `•—•` brand mark in transfer-progress state.
- Sender's current filename (center-truncated).
- Progress copy: "X.X MB of Y.Y MB".
- A [Show details →] button opens TransferDetailsScreen in its in-progress mode.
- Transfer speed: "3.2 MB/s".
- [Cancel button] in the trailing position.

#### 5. Connection paused / reconnecting

Triggered when the underlying connection drops without a graceful end (neither side tapped Cancel). Applies symmetrically to both outbound and inbound.

- Peer name.
- `•—•` in its Searching state (hollow right dot, opacity oscillation) — semantically: we are searching for the peer again.
- Copy: "Reconnecting to \<peer\>… (\<countdown\>s)".
- Countdown ticks down from `RECONNECTION_TIMEOUT` (default 15 s; final value set at implementation time).
- If connection is restored within the window: card silently resumes the Active outbound or Active inbound state.
- If `RECONNECTION_TIMEOUT` elapses without reconnection: card transitions to Error state.
- Each disconnect starts a fresh `RECONNECTION_TIMEOUT` countdown. A successful reconnect followed by a new disconnect restarts the timer from zero.
- No user action required or available in this state (no Cancel — user cannot cancel a reconnecting transfer; they can wait or the system will timeout to Error).

#### 6. Received (inbound complete)

Replaces any inline inbound card after a successful inbound transfer completes. Persistent — does not self-dismiss.

- Peer name.
- `•—•` in success state for ~700 ms (per brand-mark spec), then settles with line fully filled.
- Copy: "Received \<N\> files from \<peer\> — tap to open".
- A [Show details →] button navigates to TransferDetailsScreen for the per-file breakdown (all files listed under "Received").
- Tapping the card body attempts an OS deep-link to the saved folder.
  - If deep-link succeeds: leaves Tether / opens the OS file location.
  - If deep-link fails: an **inline hint appears within the same card** (no new screen):
    - iOS: "Open Files → On My iPhone → Tether"
    - Android: "Open Files app → Downloads → Tether"
    - macOS: "Open Finder → Downloads → Tether"
    - Desktop JVM: "Open file manager → Downloads → Tether"
  - The hint persists until [Dismiss × button] is tapped, which clears the Received state and returns the card to Idle. The hint has no separate dismiss affordance.
- [Dismiss ×] affordance in the trailing corner (explicit button, labeled with × icon; semantic label: "Dismiss received notification from \<peer\>").

**Partial-completion variant (transfer cancelled mid-batch, or connection-lost recovery):** card still enters Received for the files that DID arrive.

- Copy when sender cancelled: "Received \<X\> files from \<peer\> — sender cancelled."
- Copy when receiver cancelled: "Received \<X\> files from \<peer\> — you cancelled."
- Copy when connection lost: "Received \<X\> files from \<peer\> — connection lost."
- [Dismiss ×] affordance present.
- A [Show details →] button navigates to TransferDetailsScreen for the per-file outcome breakdown.

#### 7. Sent (outbound complete)

Symmetric to Received. Persistent — does not self-dismiss.

- Peer name.
- `•—•` in success state for ~700 ms, then settles.
- Copy: "Sent \<N\> files to \<peer\>".
- A [Show details →] button navigates to TransferDetailsScreen for the per-file breakdown (all files listed under "Received").
- [Dismiss ×] affordance in the trailing corner (semantic label: "Dismiss sent notification to \<peer\>").

**Partial-completion variant (cancelled mid-batch by receiver, connection-lost, or per-file read errors):**

- Copy when receiver cancelled: "Sent \<X\> of \<Y\> files to \<peer\> (transfer was cancelled)".
- Copy when connection lost: "Sent \<X\> of \<Y\> files to \<peer\> (connection lost)".
- Copy when files unreadable: "Sent \<X\> of \<Y\> files to \<peer\> (\<Z\> files couldn't be read)".
- A [Show details →] button navigates to TransferDetailsScreen showing which files were confirmed received and which were not sent.
- [Dismiss ×] affordance.

#### 8. Error

Persistent — does not self-dismiss.

- Peer name.
- `•—•` in error state: line truncated at failure point, right dot hollow in error tone.
- Error copy (see error matrix below).
- [Retry button] and [Dismiss × button] in trailing area. [Retry button] is disabled (grayed, not hidden) when the peer is offline.
- A [Show details →] button navigates to TransferDetailsScreen for the per-file outcome breakdown (shows the "Not sent" section even when no files arrived).

**Error matrix:**

| Trigger | Copy on card | [Retry button] re-sends | Retry available? |
|---|---|---|---|
| Network / Wi-Fi lost | "Connection lost. Try again when you're back on Wi-Fi." | Only files NOT yet received by peer | Yes; disabled if peer offline |
| Peer unreachable mid-transfer | "\<peer\> is no longer reachable. Try again." | Only files NOT yet received by peer | Yes; disabled if peer offline |
| Single file unreadable on sender | "Couldn't read \<filename\>. Other files continue." | Per-file, not a terminal error — batch continues; file surfaces in Sent state's partial-failure summary | n/a (per-file) |
| Receiver write fails (disk full) | "Couldn't save on \<peer\>. Free up space and try again." | Only files that failed on receiver side | Yes; disabled if peer offline |
| All files failed | "Couldn't send to \<peer\>. Try again." | Entire batch | Yes; disabled if peer offline |

**Universal retry rule:** [Retry button] always re-sends only files that have NOT been confirmed received. Files already on the receiver are never re-sent.

#### 9. Cancelled

Brief inline state. Persistent — does not self-dismiss.

- Peer name.
- Copy: "Cancelled".
- If files were partially received before cancel, additional copy: "\<X\> files were received before cancel. \<Y\> were not sent.".
- A [Show details →] button navigates to TransferDetailsScreen for the per-file breakdown.
- [Dismiss × button] in trailing corner.

**Cancel / partial-failure semantics:** files already received in full on the receiver stay on the receiver — no rollback. The Cancelled state on the sender surfaces this explicitly. The receiver's peer-card simultaneously enters its own Received state (partial-completion variant) for the files that did arrive.

**Interactions (all states).**

- Tap card body (Idle): initiates send flow (see DeviceListScreen interactions).
- Tap chevron `▾` (Idle collapsed): expands card to Idle (expanded).
- Tap chevron `▴` (Idle expanded): collapses card.
- Tap per-peer auto-send toggle (Idle expanded): flips preference immediately (no confirm dialog — preference is local to this peer).
- Tap [i] info icon (Idle expanded): shows tooltip / popover.
- Tap [Cancel button] (Active outbound or Active inbound): immediately cancels transfer. No confirm dialog. Both sides stop.
- Tap card body (Received state): attempts OS deep-link. May show inline hint on failure.
- Tap [Dismiss × button] (Received / Sent / Error / Cancelled): clears that state; card returns to Idle.
- Tap [Retry button] (Error, enabled): re-initiates transfer to peer with only un-received files. Card returns to Active outbound state.
- Tap [Retry button] (Error, disabled): no action (button is non-interactive; grayed).
- Tap [Show details →] (Active outbound / Active inbound / Sent / Received / Cancelled / Error): navigates to TransferDetailsScreen. In Active states the screen opens in its in-progress mode.

**Copy.**

- "\<peer name\>" (primary label, all states)
- "Online" / "Paired — offline"
- "Auto-send to this device when it's your only online device"
- "Sends immediately — no device-list tap required."
- "Tether will skip the device list and send straight to \<peer\> when no other paired devices are online." (tooltip)
- "X.X MB of Y.Y MB"
- "3.2 MB/s" (example — live value)
- "Calculating…"
- "\<N\> files skipped"
- "Cancel" (button label on Active states)
- "Reconnecting to \<peer\>… (\<countdown\>s)"
- "Received \<N\> files from \<peer\> — tap to open"
- "Received \<X\> files from \<peer\> — sender cancelled." (partial-completion, sender cancelled)
- "Received \<X\> files from \<peer\> — you cancelled." (partial-completion, receiver cancelled)
- "Received \<X\> files from \<peer\> — connection lost." (partial-completion, connection lost)
- "Open Files → On My iPhone → Tether" (iOS deep-link failure hint)
- "Open Files app → Downloads → Tether" (Android deep-link failure hint)
- "Open Finder → Downloads → Tether" (macOS deep-link failure hint)
- "Open file manager → Downloads → Tether" (Desktop JVM deep-link failure hint)
- "Sent \<N\> files to \<peer\>"
- "Sent \<X\> of \<Y\> files to \<peer\> (transfer was cancelled)"
- "Sent \<X\> of \<Y\> files to \<peer\> (connection lost)"
- "Connection lost. Try again when you're back on Wi-Fi."
- "\<peer\> is no longer reachable. Try again."
- "Couldn't read \<filename\>. Other files continue."
- "Couldn't save on \<peer\>. Free up space and try again."
- "Couldn't send to \<peer\>. Try again."
- "Retry" (button label on Error state)
- "Show details →" (navigation button label — available in Active, Sent, Received, Cancelled, and Error states)
- "Cancelled"

**Per-platform deltas.**

- Android: [Cancel button] and [Dismiss × button] hit targets meet Android minimum touch-target size. Deep-link failure hint uses Android-specific path copy.
- iOS: deep-link failure hint: "Open Files → On My iPhone → Tether". iOS suspension state: when OS suspends Tether mid-inbound transfer, on next foreground the card shows a special variant of Error with copy: "Transfer from \<peer\> was interrupted. Ask \<peer\> to send again." — a [Dismiss × button] is the only affordance (no [Retry button] since the receiver cannot retry; sender must re-initiate).
- macOS: [i] info icon uses a popover (hover or click) instead of a tooltip. Deep-link failure hint uses macOS Finder copy.
- Desktop JVM: [i] info icon uses a hover tooltip. Deep-link failure hint uses platform-appropriate file manager copy.

**Accessibility.**

- Card is a focusable container; role is `listitem` within the peer list.
- Peer name announced as the card's accessible heading.
- Status indicator ("Online" / "Paired — offline"): announced as part of the card's accessible description.
- Chevron `▾` / `▴`: semantic label "Expand \<peer\> settings" / "Collapse \<peer\> settings".
- Per-peer auto-send toggle: semantic label "Auto-send to \<peer\> when it's the only online device, currently \<On/Off\>".
- [i] info icon: semantic label "More information about auto-send".
- Active outbound / inbound: card is a live region (polite). Announces at three points only (not byte-by-byte):
  - Start: "Sending \<N\> files to \<peer\>" or "Receiving files from \<peer\>".
  - Per-file failure: "Failed to send \<filename\>" (assertive).
  - Done: "Sent \<N\> files to \<peer\>" or "Received \<N\> files from \<peer\>" (assertive).
- `•—•` mark `contentDescription` in transfer-progress state: "Transfer in progress".
- `•—•` mark `contentDescription` in success state: "Transfer complete".
- `•—•` mark `contentDescription` in error state: "Transfer failed".
- `•—•` mark `contentDescription` in reconnecting state (Searching state of the mark): "Reconnecting to peer".
- [Cancel button] semantic label: "Cancel transfer to \<peer\>" or "Cancel incoming transfer from \<peer\>".
- Reconnecting state: assertive live-region announcement: "Connection lost. Reconnecting to \<peer\>…".
- Received state card body (when tappable): role is `button`; semantic label: "Open files received from \<peer\>".
- [Dismiss × button] semantic label: "Dismiss received notification from \<peer\>" / "Dismiss sent notification to \<peer\>" / "Dismiss error for \<peer\>" / "Dismiss cancelled transfer for \<peer\>".
- [Retry button] semantic label (enabled): "Retry sending to \<peer\>".
- [Retry button] semantic label (disabled): "Retry not available — \<peer\> is offline".
- [Show details →] button: semantic label "Show transfer details for \<peer\>".
- Skip-count badge: "\<N\> files skipped so far".
- Speed label: "Transfer speed: \<value\>".
- Filename label: "Currently sending: \<filename\>" or "Currently receiving: \<filename\>".
- Keyboard focus order within card (Desktop/macOS): peer name → status → chevron → (if expanded: toggle → [i] icon) → [Cancel button] (if active) → [Show details →] (if present) → [Retry button] (if error, enabled) → [Dismiss × button] (if dismissible).

---

### MobilePickerChooserSheet

**Purpose.** A bottom sheet on Android and iOS that lets the user choose which system picker to invoke — Photos, Files, or Folder — before a send flow begins.

**Entry points.** Tapping a PeerCard body on Android or iOS when no pending outbound exists (in-app send flow).

**Layout.**

- Bottom-sheet handle at top.
- Sheet title: "Send from…"
- Three options as tappable rows with icons: "Photos", "Files", "Folder".
- Swipe down or tap outside: dismisses without action.

**States.**

- **Default:** three options listed.
- **Dismissed:** sheet gone, user returns to DeviceListScreen with no pending state.

**Interactions.**

- Tap "Photos": opens system photo picker (multi-select). On completion, if selection exceeds threshold: LargeSelectionConfirmDialog. Otherwise: card transitions to Active outbound.
- Tap "Files": opens system file picker (multi-select). Same on completion.
- Tap "Folder": opens system folder picker (single selection). Same on completion; if selection exceeds threshold: LargeSelectionConfirmDialog.
- Swipe down / tap scrim: sheet dismisses, no action, no state change.

**Copy.**

- "Send from…"
- "Photos"
- "Files"
- "Folder"

**Per-platform deltas.**

- Android: uses the OS file/folder picker. "Photos" maps to system photo picker; "Files" and "Folder" map to the OS file picker and OS folder picker respectively.
- iOS: "Photos" maps to system photo picker; "Files" and "Folder" map to the system document picker in the appropriate mode.
- macOS: this screen does not exist — system file dialog handles files and folder.
- Desktop JVM: this screen does not exist — same as macOS.

**Accessibility.**

- Bottom sheet is presented as a modal; focus moves into the sheet on open.
- Each option row has a semantic label matching its visible label ("Photos", "Files", "Folder").
- Dismiss affordance (handle, scrim): semantic label "Dismiss picker chooser".
- Focus returns to the triggering PeerCard on dismiss.

---

### LargeSelectionConfirmDialog

**Purpose.** A confirmation dialog that appears when the selected files exceed the soft threshold (>500 files OR >2 GB), guarding against an accidental "select all" scenario.

**Entry points.**

- After folder selection (MobilePickerChooserSheet on Android/iOS, or system file dialog on macOS/Desktop) when the selection exceeds the threshold.
- After multi-file picker selection that exceeds the threshold.
- After share-sheet arrival with >500 files or >2 GB.
- After drag-and-drop with >500 files or >2 GB.

**Layout.**

- Modal dialog with title, body text, a "Don't show again" checkbox, and two buttons.
- Title: "Large selection"
- Body: "About to send \<N\> files (\<size\>) to \<peer\>. Continue?"
- Checkbox: "Don't show again" (default unchecked).
- Buttons: [Cancel button] (left / secondary), [Send button] (right / primary).
- Default focus: [Cancel button].

**States.**

- **Visible (checkbox unchecked):** default. Shows with actual file count and size.
- **Visible (checkbox checked):** checkbox ticked; user intent is to suppress future appearances.
- **Dismissed (Cancel button):** dialog closes, selection is cleared, user returns to DeviceListScreen.
- **Confirmed (Send button, checkbox unchecked):** dialog closes, transfer begins (PeerCard transitions to Active outbound).
- **Confirmed (Send button, checkbox checked):** suppression preference saved; dialog closes; transfer begins. The toggle in SettingsSection — File Transfer flips to Off; the user can re-enable from Settings.

**Interactions.**

- Tap [Cancel button]: dismiss dialog, clear selection, return to DeviceListScreen.
- Tap [Send button]: close dialog, proceed; if checkbox was checked, persist suppression.
- Tap checkbox: toggles the "Don't show again" preference within the dialog (not yet saved — only saved on [Send button] tap).
- Hardware back / Escape key: same as [Cancel button].

**Copy.**

- "Large selection"
- "About to send \<N\> files (\<size\>) to \<peer\>. Continue?"
- "Don't show again"
- "Cancel"
- "Send"

**Per-platform deltas.**

- Android: system-style dialog. Hardware back = Cancel.
- iOS: system alert dialog presentation. Swipe-to-dismiss blocked (destructive default — Cancel is default).
- macOS: standard sheet attached to the app window. Checkbox renders as a native checkbox control.
- Desktop JVM: standard modal dialog. Escape = Cancel.

**Accessibility.**

- Dialog role: `alertdialog`.
- On open, focus is placed on [Cancel button].
- [Cancel button] semantic label: "Cancel — discard selection".
- [Send button] semantic label: "Send \<N\> files to \<peer\>".
- Checkbox semantic label: "Don't show this warning again for large selections".
- Escape key always triggers Cancel on Desktop/macOS.

---

### TransferDetailsScreen

**Purpose.** Supplementary per-file view of a transfer — during transit, after full success, after partial failure, after cancel, or after error. Always opt-in via [Show details →] on the PeerCard; the card remains the primary transfer surface.

**Entry points.** Tapping [Show details →] on a PeerCard in any of: Active outbound, Active inbound, Sent (full), Sent (partial), Received (full), Received (partial), Cancelled, or Error.

**Layout.**

- Top bar with back affordance and peer name as title; transfer summary as subtitle (e.g. "Receiving 3 of 8…" while active, "Received 3 of 8 files" once finished).
- Scrollable list of files grouped into sections:
  - "Received" section: files that arrived in full (file name + size).
  - "Sending" section (active states only, sender side): the file currently in flight, plus files queued. Each row shows a small inline progress bar for the active file; queued rows show file name only.
  - "Receiving" section (active states only, receiver side): same as "Sending" from the receiver's perspective.
  - "Not sent" section (terminal states only): files that did not complete (name only, no size).
- Empty sections are omitted entirely.
- Sender-side terminal states with a non-empty "Not sent" section: a [Retry all →] CTA above the list re-sends every file in "Not sent". Per-row [Retry] button on each "Not sent" entry re-sends that single file.

**States.**

- **In-progress:** list updates live. When a file finishes transferring, its row animates from the active section ("Sending" / "Receiving") into the "Received" section. Section counts update live. When the underlying transfer reaches a terminal state while this screen is open, the screen transitions in-place to the appropriate terminal layout — the active section disappears, the "Not sent" section appears if applicable, the subtitle updates, and retry affordances appear if applicable. The user is never navigated away involuntarily.
- **Terminal — all success:** only the "Received" section visible. No retry affordances (nothing failed).
- **Terminal — partial:** "Received" + "Not sent" sections. Sender side: [Retry all →] CTA + per-row [Retry]. Receiver side: no retry (cannot initiate from receiver).
- **Terminal — all failed:** only "Not sent" section. Sender side: [Retry all →] CTA visible.
- **Loading:** brief indicator while the file list materializes. Rare.

**Interactions.**

- Tap a file row in "Received" section: attempts OS deep-link to that file. Fallback to inline hint within the row if deep-link fails (same platform-specific copy as PeerCard).
- Tap [Retry button] on a "Not sent" row (sender side, peer reachable): re-initiates transfer of that single file. The row moves to a "Retrying…" state with an inline progress bar; on success, the row animates into the "Received" section; on failure, the row returns to "Not sent" with the new error inline.
- Tap [Retry button] when peer offline: button is disabled (grayed). Helper text: "\<peer\> is offline."
- Tap [Retry all →] CTA: re-initiates transfer for every file in "Not sent". Rows show inline progress and move to "Received" on success.
- Tap a file row in "Sending" / "Receiving" / "Not sent" sections (non-retry interactions): no action.
- Tap back affordance / hardware back / swipe-back: returns to DeviceListScreen. PeerCard underneath retains its current state (active or terminal).

**Copy.**

- "Sending \<X\> of \<Y\>…" / "Receiving \<X\> of \<Y\>…" (subtitle, in-progress)
- "Received \<X\> of \<Y\> files" / "Sent \<X\> of \<Y\> files" (subtitle, terminal)
- "Received" / "Sending" / "Receiving" / "Not sent" (section headers)
- Per-row: file name + size (Received); file name + inline progress bar (active row); file name only (queued / Not sent)
- "Retry" (per-row button)
- "Retry all" (CTA)
- "\<peer\> is offline." (retry-disabled helper)

**Per-platform deltas.**

- Android: hardware back returns to DeviceListScreen.
- iOS: swipe-back gesture; top bar back chevron follows iOS HIG.
- macOS / Desktop JVM: top bar back affordance rendered as ◀ button.

**Accessibility.**

- On screen entry, focus moves to the first list item (or loading indicator).
- Back affordance semantic label: "Back to device list".
- File row in "Received": role `button`; semantic label "Open \<filename\> in file manager".
- File row in "Sending" / "Receiving" (currently active): role `text`; semantic label "\<filename\>, in progress, \<percent\> percent".
- File row in "Not sent": role `text`; semantic label "\<filename\>, not sent".
- [Retry button] (per-row, enabled): semantic label "Retry sending \<filename\>".
- [Retry button] (per-row, disabled): "Retry not available — \<peer\> is offline".
- [Retry all →] CTA: semantic label "Retry all \<N\> failed files".
- Live region (polite): announces "Received \<filename\>" as each file completes during in-progress state. Announcements are paced to remain intelligible on fast batches.
- Section headers announced as headings.

---

### SettingsSection — File Transfer

**Purpose.** The settings area where the user configures the save location and large-selection warning preferences. The per-peer auto-send toggle lives in the expanded PeerCard, not here.

**Entry points.** App settings surface (existing) — a dedicated "File Transfer" section within it.

**Layout.**

- Section header: "File Transfer"
- Save location row: label "Save location"; value shows the current path (e.g. "Downloads/Tether/"); on editable platforms, a disclosure affordance (chevron or "Change" link) opens the system folder picker. On iOS: read-only, no disclosure affordance.
- Large-selection warning toggle row: label "Show large-selection warnings"; toggle control (On by default). When Off, LargeSelectionConfirmDialog is suppressed globally.

Auto-send is configured per-peer via the expanded PeerCard (see PeerCard § Idle expanded).

**States.**

- **Android (editable):** save location shows current path; tap row → system folder picker to change.
- **iOS (read-only):** save location shows "On My iPhone → Tether/"; no change affordance. A caption beneath: "iOS does not allow changing this location."
- **macOS (editable):** save location shows current path (default: "~/Downloads/Tether/"); disclosure affordance → system folder picker (Open Panel).
- **Desktop JVM (editable):** save location shows current path (default: "Downloads/Tether/"); disclosure affordance → system folder picker.
- All platforms: large-selection warning toggle is present and functions identically.

**Interactions.**

- Tap save location row (editable platforms): opens system folder picker. On confirmation, path updates in the row.
- Tap save location row (iOS): no action.
- Tap large-selection warning toggle: flips On/Off immediately. When flipped to On, re-enables LargeSelectionConfirmDialog for future large selections.

**Copy.**

- "File Transfer"
- "Save location"
- "Downloads/Tether/" (or platform-specific default path)
- "On My iPhone → Tether/" (iOS)
- "iOS does not allow changing this location."
- "Show large-selection warnings"

**Per-platform deltas.**

- Android: save location editable via the OS folder picker.
- iOS: save location read-only with explanatory caption.
- macOS: save location editable via system folder picker (Open Panel, folder selection mode).
- Desktop JVM: save location editable via system folder picker.

**Accessibility.**

- Save location row (editable): semantic label "Change save location, currently \<path\>".
- Save location row (iOS, read-only): semantic label "Save location: On My iPhone → Tether/. This location cannot be changed on iOS."
- Large-selection warning toggle: semantic label "Show large-selection warnings, currently \<On/Off\>".

---

## Flows

### Flow 1 — In-app send, N files, already paired

1. User opens Tether → DeviceListScreen in populated state. PeerCards in Idle (collapsed).
2. User taps target PeerCard body.
3. **Android/iOS:** MobilePickerChooserSheet appears. User taps "Photos" or "Files". System picker opens. User selects files. Sheet closes.
   **macOS/Desktop:** System file dialog opens directly. User selects files.
4. If selection exceeds threshold (>500 files OR >2 GB): LargeSelectionConfirmDialog appears. User taps [Send button].
5. PeerCard transitions to Active outbound. `•—•` brand mark fills left-to-right. Filename, progress copy ("X.X MB of Y.Y MB"), and speed update live. Receiver's same PeerCard transitions to Active inbound.
6. Transfer completes. `•—•` plays success animation. Sender's PeerCard transitions to Sent state: "Sent \<N\> files to \<peer\>". Receiver's PeerCard transitions to Received state: "Received \<N\> files from \<peer\> — tap to open". Both states persist until dismissed.

### Flow 2 — Share-sheet entry, already paired

1. User is in Photos / Files app. Taps Share → Tether.
2. Tether opens at DeviceListScreen with pending-outbound banner: "Ready to send \<N\> files (\<size\>). Pick a device below." PeerCards visible in Idle state.
3. User taps target PeerCard body.
4. If selection exceeds threshold: LargeSelectionConfirmDialog. Otherwise: proceeds directly.
5. PeerCard transitions to Active outbound — same as Flow 1 from step 5.

### Flow 3 — Auto-send ON, one peer online (share-sheet entry)

1. User taps Share → Tether (or drags files onto the Tether window on macOS/Desktop). Per-peer auto-send toggle for the sole online peer is On.
2. DeviceListScreen opens. PeerCard transitions immediately to Active outbound without requiring a device-list tap.
3. Transfer begins. PeerCard shows Active outbound state.

### Flow 4 — First-time auto-send discovery via PeerCard expansion

1. User opens Tether with one paired peer online.
2. User taps the chevron `▾` on that PeerCard → card expands to Idle (expanded).
3. User sees per-peer auto-send toggle (Off by default) and [i] info icon.
4. User taps [i] info icon → tooltip/popover: "Tether will skip the device list and send straight to \<peer\> when no other paired devices are online."
5. User flips toggle to On → preference saved immediately. No confirm dialog. Next share-sheet arrival with this peer as sole online peer will auto-send.

### Flow 5 — Partial batch failure and retry

1. Transfer completes with some file failures (per-file errors during send).
2. Sender's PeerCard transitions to Sent state (partial-completion variant): "Sent \<X\> of \<Y\> files to \<peer\> (\<Z\> files couldn't be read)". [Show details →] button and [Retry button] present. [Dismiss ×] present.
3. User taps [Show details →] → TransferDetailsScreen opens; shows "Received \<X\> files" and "Not sent \<Y\> files" sections.
4. User returns to DeviceListScreen (back). Taps [Retry button] on PeerCard (peer still online) → card returns to Active outbound for only the un-received files.
5. If retry succeeds: PeerCard transitions to Sent state again for the retried batch.
6. If peer went offline: [Retry button] is disabled (grayed); card remains in Error state awaiting dismissal.

### Flow 6 — Cancel mid-transfer (sender)

1. Transfer is in progress. Sender's PeerCard is in Active outbound.
2. Sender taps [Cancel button] on the PeerCard.
3. Both sides stop immediately. No confirm dialog.
4. Files already received in full on the receiver stay on the receiver.
5. Sender's PeerCard transitions to Cancelled state: "Cancelled. \<X\> files were received before cancel. \<Y\> were not sent." [Show details →] button available → navigates to TransferDetailsScreen.
6. Receiver's PeerCard transitions to Received state (partial-completion variant): "Received \<X\> files from \<peer\> — sender cancelled."
7. Both states persist until [Dismiss × button] is tapped; [Dismiss × button] returns card to Idle.

### Flow 7 — Cancel mid-transfer (receiver)

1. Receiver's PeerCard is in Active inbound.
2. Receiver taps [Cancel button] on PeerCard.
3. Both sides stop immediately.
4. Files already received in full stay on the receiver.
5. Receiver's PeerCard transitions to Received state (partial-completion variant): "Received \<X\> files from \<peer\> — you cancelled."
6. Sender's PeerCard transitions to Cancelled state: "Sent \<X\> of \<Y\> files to \<peer\> (transfer was cancelled)".

### Flow 8 — Connection lost mid-transfer

1. Transfer is in progress. Wi-Fi drops or peer becomes unreachable.
2. PeerCard transitions to Connection paused / reconnecting state: "Reconnecting to \<peer\>… (\<countdown\>s)". Countdown from `RECONNECTION_TIMEOUT` (default 15 s).
3. If connection restores within `RECONNECTION_TIMEOUT`: transfer resumes silently. PeerCard returns to Active outbound / Active inbound.
4. If `RECONNECTION_TIMEOUT` elapses without reconnection: PeerCard transitions to Error state with appropriate error copy.
5. User taps [Retry button]: re-initiates transfer to the same peer (if peer is reachable). Card returns to Active outbound.
6. User taps [Dismiss × button] (on PeerCard error): card returns to Idle.

### Flow 9 — iOS foreground suspension during inbound transfer

1. Inbound transfer is in progress on receiver's iOS device. Receiver's PeerCard is in Active inbound.
2. User locks screen or OS suspends Tether.
3. Transfer dies. No completion notification fires. Partial file discarded.
4. User brings Tether to foreground.
5. Receiver's PeerCard transitions to iOS-suspension Error variant: "Transfer from \<peer\> was interrupted. Ask \<peer\> to send again." [Dismiss × button] only (no [Retry button]).
6. Sender's PeerCard transitions to Error state: "\<peer\> is no longer reachable. Try again."

### Flow 10 — Drag-and-drop onto Tether window (macOS / Desktop)

1. User drags file(s) from Finder / File Explorer onto the Tether window.
2. DeviceListScreen shows pending-outbound banner: "Ready to send \<N\> files (\<size\>). Pick a device below."
3. User clicks target PeerCard body.
4. No picker sheet — files already selected. If threshold exceeded: LargeSelectionConfirmDialog. Otherwise: PeerCard transitions to Active outbound.

### Flow 11 — View transfer details (per-file)

1. A transfer is in any non-Idle state (Active outbound, Active inbound, Sent, Received, Cancelled, Error). PeerCard shows [Show details →] button.
2. User taps [Show details →] → TransferDetailsScreen opens. In active states, the screen renders in in-progress mode; in terminal states, the appropriate terminal layout.
3. Sections render per the screen's state contract: "Received" / "Sending" / "Receiving" / "Not sent". Empty sections are omitted.
4. User taps a file in "Received" section → OS deep-link to that file; fallback to inline hint if deep-link fails.
5. Sender-side, partial terminal state: user taps [Retry button] on a "Not sent" row → that file is re-sent; row moves to "Received" on success or shows new error on failure. [Retry all →] CTA re-sends every "Not sent" file at once.
6. User taps back → returns to DeviceListScreen; PeerCard underneath retains its current state.

---

## Navigation

**DeviceListScreen** is the root screen. It is never replaced — it is always beneath any other screen in the stack.

**MobilePickerChooserSheet** is a bottom-sheet modal overlaid on DeviceListScreen (Android/iOS only). Dismissing it returns focus to DeviceListScreen without navigating anywhere.

**LargeSelectionConfirmDialog** is a modal dialog. It can appear over DeviceListScreen (if triggered from the picker sheet or from drag-drop). Dismissing returns to DeviceListScreen.

**TransferDetailsScreen** is pushed onto the navigation stack from DeviceListScreen (triggered by tapping [Show details →] on a PeerCard in any non-Idle state). Back returns to DeviceListScreen; the PeerCard underneath retains its current state (active or terminal).

**SettingsSection — File Transfer** lives within the existing settings navigation surface. It does not introduce a new navigation root.

**PeerCard** is not a navigable destination — it is an inline component within DeviceListScreen's scrollable list. Its state transitions do not involve navigation stack changes.

---

## Platform notes — Sleep and wake lock

### Android

- Receive-side runs in a foreground service which holds an existing wake-lock (see `docs/knowledge/android-fgs.md`). FGS also exempts the app from Doze; sockets survive screen-off.
- Sender-side: covered by engineering verification under #195.

### iOS

- **Sleep prevention.** The OS auto-lock idle timer is suppressed during an active foreground transfer — the screen does not dim or lock while transferring.
- **Foreground-only transport (separate constraint).** Manual lock (side button) and backgrounding end the session; iOS does not permit the transfer to continue. This is surfaced to the user via the persistent banner (see DeviceListScreen layout).

### macOS

- Active transfer (both send and receive) holds an OS sleep-prevention assertion for the duration. The assertion is released on completion, cancellation, or error. Lid-close (clamshell) sleep tears down sockets regardless; transfer ends on lid-close.

### Desktop JVM

- **Windows:** an OS "stay awake while busy" execution-state assertion is held during active transfer. The assertion is released on completion or cancellation.
- **Linux:** sleep inhibition is requested via the standard inhibit interface. Reliable on systemd-based distributions; behavior on non-systemd setups is best-effort only.

---

## Conceptual components

1. **PeerCard** — inline card within DeviceListScreen's peer list; a state machine covering nine states (Idle collapsed, Idle expanded, Active outbound, Active inbound, Connection paused/reconnecting, Received, Sent, Error, Cancelled); the single surface for all per-peer interaction.
2. **PeerCard auto-send toggle** — per-peer toggle with [i] info affordance; lives in PeerCard Idle (expanded); drives the auto-send preference for that specific peer.
3. **Transfer progress mark** — the `•—•` brand mark in transfer-progress state (line fills left-to-right). Used in PeerCard Active states.
4. **Transfer success mark** — the `•—•` in success state (~700 ms animation). Used in PeerCard Received/Sent states.
5. **Transfer error mark** — the `•—•` in error state (line truncated, right dot hollow in error tone). Used in PeerCard Error state.
6. **Transfer reconnecting mark** — the `•—•` in its Searching state (hollow right dot, opacity oscillation). Used in PeerCard Connection paused/reconnecting state to indicate the app is searching for the peer again.
7. **Pending-outbound banner** — non-dismissible strip above peer-cards; persistent until peer chosen or [Cancel button] tapped; no self-dismiss.
8. **iOS foreground constraint banner** — persistent non-dismissible system-style banner informing the user to keep Tether open during transfers; iOS only.
9. **Current-file label** — one-line center-truncated filename display. Used on PeerCard Active states.
10. **Progress and speed label pair** — "X.X MB of Y.Y MB" and "3.2 MB/s" shown together; always both visible during active transfer.
11. **Skip-count badge** — muted-tone secondary badge showing running file-skip count. Used on PeerCard Active outbound.
12. **Picker chooser sheet (mobile)** — bottom sheet with three tappable source options (Photos / Files / Folder). Android + iOS only.
13. **Large-selection confirm dialog** — destructive-default modal dialog with real file count and size; "Don't show again" checkbox; default focus on [Cancel button]. Applies on all platforms wherever a selection exceeds the threshold.
14. **Per-file outcome list** — scrollable list within TransferDetailsScreen; up to four sections ("Received" with file name + size, "Sending" with inline progress, "Receiving" with inline progress, "Not sent" with file name only); empty sections omitted.
15. **Retry-failed-files button** — primary button on Error PeerCard that is disabled (grayed) when the peer is offline.
16. **Navigational transfer-details button** — [Show details →] button on PeerCard in Active outbound, Active inbound, Sent, Received, Cancelled, and Error states; opens TransferDetailsScreen.
17. **Per-file retry button** — [Retry button] on each row in the "Not sent" section of TransferDetailsScreen; sender-side only; disabled when peer is offline. Successful retry moves the row into the "Received" section live.
18. **Retry-all-failed CTA** — [Retry all →] button above the file list on TransferDetailsScreen when "Not sent" section is non-empty; sender-side only.
19. **Deep-link failure hint** — inline platform-specific copy that appears within PeerCard Received state (or within a TransferDetailsScreen file row) when the OS deep-link to the saved folder fails.
20. **macOS/Desktop window-close transfer warning** — sheet attached to the window when the user closes Tether mid-transfer.
21. **Settings save-location row** — editable (with system folder picker disclosure) on Android/macOS/Desktop; read-only with explanatory caption on iOS.
22. **Settings large-selection warning toggle** — toggle in SettingsSection — File Transfer; On by default; re-enables LargeSelectionConfirmDialog after "Don't show again" suppression.

---

## Open UX questions

These are non-blocking unless noted. None gate the current implementation unless marked otherwise.

1. **Receiver-side retry for batch failures.** Sender-side retry is in scope for this feature; receiver-initiated retry is deferred. A receiver-side retry — where the receiver itself requests the sender to re-send only the failed files after freeing space — requires a pull-protocol shape, a decision on how long the failed-batch reference persists, and whether the sender must still be online. Deferred until the sender-side retry ships and usage signals the demand.

2. **Mobile picker unification — the "two taps" gap.** The MobilePickerChooserSheet adds one tap to the vision's "two taps to send" on Android and iOS, caused by OS constraints (the OS file/folder pickers do not support mixing folder and multi-file selection in a single picker session). Revisit once OS support evolves or an in-app picker becomes viable.

3. **iOS deep-link to Files app reliability.** The inline hint in PeerCard Received state ("Open Files → On My iPhone → Tether") is a static instruction when the deep-link fails. If the deep-link proves unreliable in practice, a more guided in-app flow may be needed. Monitor failure rate post-launch.

4. **Receiver-cancel copy symmetry.** Flow 7 uses "Received \<X\> files from \<peer\> — you cancelled." for the receiver's own Received partial-completion state. An alternative is "…— transfer ended early." which is less accusatory. Confirm preferred framing before implementation.

5. **Edit pending files.** When a pending outbound exists, a future enhancement is to allow adding/removing files from the pending selection before tapping a peer. Deferred — not in MVP scope.

6. **Sender-side wake-lock parity (engineering).** Each platform holds the strongest sleep-prevention mechanism available for the duration of a transfer: Android — FGS covers receive-side, send-side adds screen-keep-on + partial wake-lock; iOS — auto-lock idle-timer suppression (foreground only); macOS — OS sleep-prevention assertion (both directions); Windows — execution-state assertion (send); Linux — inhibit interface (send; best-effort on non-systemd). Engineering verification and implementation tracking is owned by #195.
