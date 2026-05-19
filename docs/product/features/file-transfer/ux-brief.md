# UX brief — File transfer

**Spec:** [spec.md](spec.md)
**Status:** `ready`

---

## Information architecture

This feature introduces four new screens / dialogs plus one settings section, touches one existing screen (DeviceListScreen). All per-peer transfer state is contained within PeerCard — there are no separate incoming-transfer cards, auto-pick prompts, or iOS-only received screens.

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
      │                                               SendProgressScreen
      │                                                           │
      │                                           TransferSummaryScreen
      │
      └── [tap peer, idle, no pending outbound] ─────────────────┐
                                                                  ▼
                                          MobilePickerChooserSheet (Android + iOS only)
                                                                  │
                                                     [large selection?] ─── LargeSelectionConfirmDialog
                                                                  │
                                                       SendProgressScreen
                                                                  │
                                                       TransferSummaryScreen

SettingsSection — File Transfer  (inside the app's existing settings surface)
```

Screens introduced: SendProgressScreen, LargeSelectionConfirmDialog, TransferSummaryScreen, MobilePickerChooserSheet, SettingsSection — File Transfer.

Screens touched: DeviceListScreen (peer rows replaced by PeerCards with full state machine).

---

## Screens

### DeviceListScreen

**Purpose.** The home screen where the user sees online peers (as PeerCards) and manages any in-flight or pending transfer state.

**Entry points.** App launch; OS share-sheet / "Open with" routing Tether to foreground with files pre-selected; drag-and-drop files onto the Tether window (macOS/Desktop); minimize from SendProgressScreen.

**Layout.**

- Top bar: app title only. No settings affordance (settings is a separate feature).
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
- Tap PeerCard body (idle, pending outbound exists): initiates send to that peer. On Android/iOS: first opens LargeSelectionConfirmDialog if threshold exceeded, then transitions card to Active outbound. On macOS/Desktop: files already selected via drag-drop or system dialog — proceeds directly.
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
- [Cancel button] in the trailing position (explicitly a button, visually distinct, labeled "Cancel").

#### 4. Active inbound (receiving)

Symmetric to Active outbound. Card swells.

- Peer name (top row, prefixed: "From \<peer\>").
- `•—•` brand mark in transfer-progress state.
- Sender's current filename (center-truncated).
- Progress copy: "X.X MB of Y.Y MB".
- Transfer speed: "3.2 MB/s".
- [Cancel button] in the trailing position.

#### 5. Connection paused / reconnecting

Triggered when the underlying connection drops without a graceful end (neither side tapped Cancel). Applies symmetrically to both outbound and inbound.

- Peer name.
- `•—•` in its Searching state (hollow right dot, opacity oscillation) — semantically: we are searching for the peer again.
- Copy: "Reconnecting to \<peer\>… (\<countdown\>s)".
- Countdown ticks down from 15 seconds.
- If connection is restored within the window: card silently resumes the Active outbound or Active inbound state.
- If 15 seconds elapse without reconnection: card transitions to Error state.
- Each disconnect starts a fresh 15-second countdown. A successful reconnect followed by a new disconnect restarts the timer from zero.
- No user action required or available in this state (no Cancel — user cannot cancel a reconnecting transfer; they can wait or the system will timeout to Error).

#### 6. Received (inbound complete)

Replaces any inline inbound card after a successful inbound transfer completes. Persistent — does not self-dismiss.

- Peer name.
- `•—•` in success state for ~700 ms (per brand-mark spec), then settles with line fully filled.
- Copy: "Received \<N\> files from \<peer\> — tap to open".
- Tapping the card body attempts an OS deep-link to the saved folder.
  - If deep-link succeeds: leaves Tether / opens the OS file location.
  - If deep-link fails: an **inline hint appears within the same card** (no new screen):
    - iOS: "Open Files → On My iPhone → Tether"
    - Android: "Open Files app → Downloads → Tether"
    - macOS: "Open Finder → Downloads → Tether"
    - Desktop JVM: "Open file manager → Downloads → Tether"
  - The hint persists within the card until the entire card is dismissed via [Dismiss × button]. No separate dismiss affordance for the hint itself.
- [Dismiss ×] affordance in the trailing corner (explicit button, labeled with × icon; semantic label: "Dismiss received notification from \<peer\>").

**Partial-completion variant (transfer cancelled mid-batch, or connection-lost recovery):** card still enters Received for the files that DID arrive.

- Copy when sender cancelled: "Received \<X\> files from \<peer\> — sender cancelled."
- Copy when receiver cancelled: "Received \<X\> files from \<peer\> — you cancelled."
- Copy when connection lost: "Received \<X\> files from \<peer\> — connection lost."
- [Dismiss ×] affordance present.
- A [Show received files button] / [Hide received files button] toggle expands an inline list of the filenames that actually arrived.

#### 7. Sent (outbound complete)

Symmetric to Received. Persistent — does not self-dismiss.

- Peer name.
- `•—•` in success state for ~700 ms, then settles.
- Copy: "Sent \<N\> files to \<peer\>".
- [Dismiss ×] affordance in the trailing corner (semantic label: "Dismiss sent notification to \<peer\>").

**Partial-completion variant (cancelled mid-batch by receiver or connection-lost):**

- Copy: "Sent \<X\> of \<Y\> files to \<peer\> (transfer was cancelled)" or "… (connection lost)".
- [Show received files button] / [Hide received files button] reveals which files were confirmed received. Copy in the list header: "\<X\> files were received before the transfer ended. \<Y\> were not sent."
- [Dismiss ×] affordance.

#### 8. Error

Persistent — does not self-dismiss.

- Peer name.
- `•—•` in error state: line truncated at failure point, right dot hollow in error tone.
- Error copy (see error matrix below).
- [Retry button] and [Dismiss × button] in trailing area. [Retry button] is disabled (grayed, not hidden) when the peer is offline.

- [Show received files button] / [Hide received files button] toggle (appears only when ≥1 file was confirmed received before the error): expands an inline list of filenames that arrived successfully before the transfer failed. Behaviour is identical to the Cancelled state's affordance.

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
- If files were partially received before cancel, additional copy: "\<X\> files were received before cancel. \<Y\> were not sent." with [Show received files button] / [Hide received files button] toggle.
- [Dismiss × button] in trailing corner.

**Cancel / partial-failure semantics:** files already received in full on the receiver stay on the receiver — no rollback. The Cancelled state on the sender surfaces this explicitly. The receiver's peer-card simultaneously enters its own Received state (partial-completion variant) for the files that did arrive.

**Interactions (all states).**

- Tap card body (Idle): initiates send flow (see DeviceListScreen interactions).
- Tap chevron `▾` (Idle collapsed): expands card to Idle (expanded).
- Tap chevron `▴` (Idle expanded): collapses card.
- Tap per-peer auto-send toggle (Idle expanded): flips preference immediately (no confirm dialog — preference is local to this peer). No auto-send confirmation dialog anywhere in the flow.
- Tap [i] info icon (Idle expanded): shows tooltip / popover.
- Tap [Cancel button] (Active outbound or Active inbound): immediately cancels transfer. No confirm dialog. Both sides stop.
- Tap card body (Received state): attempts OS deep-link. May show inline hint on failure.
- Tap [Dismiss × button] (Received / Sent / Error / Cancelled): clears that state, card returns to Idle.
- Tap [Retry button] (Error, enabled): re-initiates transfer to peer with only un-received files. Card returns to Active outbound state.
- Tap [Retry button] (Error, disabled): no action (button is non-interactive; grayed).
- Tap [Show received files button]: expands inline file list within card; button label changes to [Hide received files button].
- Tap [Hide received files button]: collapses the list; button label returns to [Show received files button].

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
- "\<X\> files were received before the transfer ended. \<Y\> were not sent."
- "\<X\> files were received before cancel. \<Y\> were not sent."
- "Connection lost. Try again when you're back on Wi-Fi."
- "\<peer\> is no longer reachable. Try again."
- "Couldn't read \<filename\>. Other files continue."
- "Couldn't save on \<peer\>. Free up space and try again."
- "Couldn't send to \<peer\>. Try again."
- "Retry" (button label on Error state)
- "Show received files" / "Hide received files" (toggle button labels — Error state, Cancelled state, and Sent partial-completion state)
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
- [Show received files button] / [Hide received files button]: semantic labels match visible labels.
- Skip-count badge: "\<N\> files skipped so far".
- Speed label: "Transfer speed: \<value\>".
- Filename label: "Currently sending: \<filename\>" or "Currently receiving: \<filename\>".
- Keyboard focus order within card (Desktop/macOS): peer name → status → chevron → (if expanded: toggle → [i] icon) → [Cancel button] (if active) → [Retry button] (if error, enabled) → [Dismiss × button] (if dismissible).

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
- **Confirmed (Send button, checkbox checked):** preference saved to suppress future dialog appearances; dialog closes; transfer begins. There is no in-feature way to re-enable the dialog once suppressed (see Open UX questions).

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
- macOS: standard sheet attached to the app window. Note: checkbox renders as a native checkbox control.
- Desktop JVM: standard modal dialog. Escape = Cancel.

**Accessibility.**

- Dialog role: `alertdialog`.
- On open, focus is placed on [Cancel button].
- [Cancel button] semantic label: "Cancel — discard selection".
- [Send button] semantic label: "Send \<N\> files to \<peer\>".
- Checkbox semantic label: "Don't show this warning again for large selections".
- Escape key always triggers Cancel on Desktop/macOS.

---

### SendProgressScreen

**Purpose.** The sender's optional focus-mode screen during an active transfer, showing batch progress in full-screen form. This screen is an alternative view of the same transfer already represented by PeerCard in Active outbound state — both show the same transfer simultaneously.

**Disposition:** kept. The PeerCard swelled state is compact and embeds in a scrollable list; SendProgressScreen provides an unobstructed, full-attention view for large batches or users who prefer it. The two surfaces are complementary, not redundant.

**Entry points.**

- Automatically after the user taps a PeerCard (idle) and the file selection is confirmed, on Android and iOS (push onto stack).
- Tapping a PeerCard that is already in Active outbound state re-opens SendProgressScreen for that transfer.
- On macOS/Desktop: fills the main content area; no push animation.

**Back button / minimize behaviour.**

- **Back gesture / hardware back (Android) / swipe-back (iOS):** minimizes to DeviceListScreen. The transfer continues; PeerCard shows Active outbound state. This is minimize, NOT cancel.
- **Minimize button ▾** in the screen's top bar (explicit affordance in addition to the gesture): same minimize action.
- Back / minimize is NEVER suppressed — the user can always return to DeviceListScreen without cancelling.
- **[Cancel button]:** a separate, visually distinct, destructive affordance in the **trailing** position of the top bar. Labeled "Cancel" in text (not just an icon). Cancels the transfer immediately. No confirm dialog.
- The distinction between minimize (back / ▾) and cancel ([Cancel button]) must be visually unambiguous — they cannot share position or visual weight.

**Layout.**

- Top bar: peer name as title; minimize button ▾ in the leading position; [Cancel button] in the trailing position.
- Center region: the `•—•` brand mark in transfer-progress state (line fills left-to-right proportional to total bytes transferred). The mark is the only progress bar; no secondary bar.
- Below the mark: current filename (one line, center-truncated with ellipsis in the middle).
- Below filename: progress copy "X.X MB of Y.Y MB" AND transfer speed "3.2 MB/s" — both always visible.
- Skip-count badge (appears only when ≥1 file skipped): muted-tone secondary badge near the filename: "\<N\> files skipped".

**States.**

- **Preparing (connection established, enumeration in progress):** `•—•` in transfer-progress state at 0%; below the mark: "Preparing…"; progress and speed fields: empty.
- **In-progress:** `•—•` filling left-to-right; filename shows current file being sent; speed shows "Calculating…" for first ~3 s, then live value. Skip badge appears if any files are skipped.
- **Connection paused / reconnecting:** `•—•` in its Searching state (hollow right dot, opacity oscillation) — semantically: we are searching for the peer again. Copy: "Reconnecting to \<peer\>… (\<countdown\>s)". No [Cancel button] — user waits for timeout or reconnection. If reconnected within 15 s: silently resumes. If not: transitions to Error state.
- **Completed (all files sent):** `•—•` in success state (~700 ms); screen transitions automatically to TransferSummaryScreen.
- **Completed (partial failures):** same success animation, then TransferSummaryScreen showing partial-failure summary.
- **All failed:** `•—•` in error state; transitions to TransferSummaryScreen.
- **Error (network / peer lost, after reconnect timeout):** `•—•` in error state; error copy per error matrix (same as PeerCard Error state); [Retry button] (enabled if peer reachable, disabled if not) and [Done button].
- **Cancelled:** brief neutral-tone "Cancelled" text on screen, then minimizes to DeviceListScreen (PeerCard shows Cancelled state).

**Interactions.**

- Tap minimize button ▾ or back gesture: minimize to DeviceListScreen. Transfer continues.
- Tap [Cancel button]: immediately cancels transfer; no confirm dialog. Screen shows "Cancelled" briefly then minimizes to DeviceListScreen (PeerCard transitions to Cancelled state).
- Tap [Retry button] (Error state, enabled): re-initiates transfer to the same peer (only un-received files). Returns to in-progress state.
- Tap [Done button] (Error state): minimizes to DeviceListScreen (PeerCard remains in Error state).

**Copy.**

- "\<peer name\>" (top bar title)
- "Cancel" ([Cancel button] label)
- "Preparing…"
- "Calculating…"
- "X.X MB of Y.Y MB"
- "3.2 MB/s" (example — live value)
- "\<filename\>" (center-truncated)
- "\<N\> files skipped"
- "Reconnecting to \<peer\>… (\<countdown\>s)"
- "Connection lost. Try again when you're back on Wi-Fi."
- "\<peer\> is no longer reachable. Try again."
- "Couldn't save on \<peer\>. Free up space and try again."
- "Retry"
- "Done"
- "Cancelled"

**Per-platform deltas.**

- Android: minimize button ▾ in top bar leading slot. Hardware back = minimize (overrides the default back = pop behavior). [Cancel button] in trailing slot. FGS runs in background — no foreground constraint banner on this screen.
- iOS: minimize button ▾ in top bar leading slot (replaces the default back chevron). Swipe-back gesture = minimize (NOT disabled). [Cancel button] in trailing slot. Foreground constraint banner rendered persistently at top: "Keep Tether open to complete the transfer."
- macOS: minimize button ▾ in toolbar. [Cancel button] in toolbar trailing area. Window close button warns if transfer active: sheet attached to window — "A transfer is in progress. Cancel it and close?" [Keep Sending button] [Cancel Transfer button].
- Desktop JVM: same as macOS for window-close warning.

**Accessibility.**

- `•—•` mark `contentDescription` updates at defined live-region announcement points only (not byte-by-byte):
  - Start: "Sending \<N\> files to \<peer\>".
  - Per-file failure: "Failed to send \<filename\>" (assertive).
  - Done: "Sent \<N\> files" or "Sent \<X\> of \<Y\> files, \<Z\> failed".
- Live-region role: `polite` except for failure announcements which are `assertive`.
- Minimize button ▾ semantic label: "Minimize — return to device list, transfer continues".
- [Cancel button] semantic label: "Cancel transfer to \<peer\>".
- Speed label semantic: "Transfer speed: \<value\>".
- Filename label semantic: "Currently sending: \<filename\>".
- Skip-count badge semantic: "\<N\> files skipped so far".
- [Retry button] semantic label: "Retry sending to \<peer\>".
- [Done button] semantic label: "Return to device list".
- Keyboard focus order (Desktop/macOS): minimize button → [Cancel button] → content area (read-only) → [Retry button] / [Done button] in error state.

---

### TransferSummaryScreen

**Purpose.** The end-of-batch result screen shown after every completed send (on the sender's side), covering all-success, partial-failure, and all-failed outcomes.

**Entry points.** Automatically after SendProgressScreen completes (whether fully successful, partial-failure, or all-failed). Not reachable directly.

**Layout.**

- Top bar: "Transfer complete" title; back/close affordance (returns to DeviceListScreen).
- `•—•` in success state (all success) or error state (any failures).
- Primary summary line (large text).
- Secondary detail (expandable for failure list).
- [Retry failed files button] (visible only when there are failed files AND the peer is still reachable).
- [Done button] (always visible — returns to DeviceListScreen).

**States.**

- **All success:** `•—•` in success state. Primary: "Sent \<N\> files to \<peer\>." No retry button. [Done button].
- **Partial failure (collapsed):** `•—•` in error state. Primary: "\<X\> of \<Y\> files sent. \<K\> failed." [Show details button]. [Retry failed files button] (enabled if peer still reachable; disabled with helper text "\<peer\> is no longer reachable" if peer has left mDNS). [Done button].
- **Partial failure (expanded):** same as collapsed but [Show details button] becomes [Hide details button]; a scrollable list of failed filenames appears below the primary line.
- **All failed:** `•—•` in error state. Primary: "0 of \<Y\> files sent. All failed." [Show details button] / [Hide details button] and same retry/done button behavior.
- **Retry in progress:** [Retry failed files button] transitions to loading state ("Retrying…"); the screen effectively becomes SendProgressScreen again for the failed-files subset.
- **Peer gone (retry unavailable):** [Retry failed files button] visible but disabled. Helper text: "\<peer\> is no longer reachable."

**Interactions.**

- Tap [Show details button]: expands the failed-file list in place; button label changes to [Hide details button].
- Tap [Hide details button]: collapses the list; button label returns to [Show details button].
- Tap [Retry failed files button] (enabled): re-initiates transfer to the same peer for only the un-received files. Navigates to SendProgressScreen.
- Tap [Retry failed files button] (disabled): no action (button is non-interactive with helper text).
- Tap [Done button] or back affordance: returns to DeviceListScreen.
- Hardware back (Android) / swipe-back (iOS): same as [Done button].

**Copy.**

- "Transfer complete"
- "Sent \<N\> files to \<peer\>."
- "\<X\> of \<Y\> files sent. \<K\> failed."
- "0 of \<Y\> files sent. All failed."
- "Show details"
- "Hide details"
- "Retry failed files"
- "Retrying…"
- "\<peer\> is no longer reachable."
- "Done"

**Per-platform deltas.**

- Android: hardware back returns to DeviceListScreen (same as [Done button]).
- iOS: swipe-back returns to DeviceListScreen. Top bar shows a standard back chevron.
- macOS: top bar shows close affordance (×). Returns to DeviceListScreen within the same window.
- Desktop JVM: same as macOS.

**Accessibility.**

- `•—•` mark `contentDescription`: "Transfer complete" (success) or "Transfer completed with failures" (any failures).
- [Show details button] / [Hide details button]: semantic label matches visible label exactly.
- [Retry failed files button] semantic label when disabled: "Retry not available — \<peer\> is no longer reachable."
- Failed file list: each filename is read-only text, not interactive.
- Live-region announcement on screen entry (assertive): the primary summary line is announced once.
- Keyboard focus order (Desktop/macOS): back/close → summary text → expand/collapse button → [Retry failed files button] (if present) → [Done button].

---

### SettingsSection — File Transfer

**Purpose.** The settings area where the user configures the save location. The per-peer auto-send toggle lives in the expanded PeerCard, not here.

**Entry points.** App settings surface (existing) — a dedicated "File Transfer" section within it.

**Layout.**

- Section header: "File Transfer"
- Save location row: label "Save location"; value shows the current path (e.g. "Downloads/Tether/"); on editable platforms, a disclosure affordance (chevron or "Change" link) opens the system folder picker. On iOS: read-only, no disclosure affordance.

There is no auto-send toggle row in this section — auto-send is configured per-peer via the expanded PeerCard.

**States.**

- **Android (editable):** save location shows current path; tap row → system folder picker to change.
- **iOS (read-only):** save location shows "On My iPhone → Tether/"; no change affordance. A caption beneath: "iOS does not allow changing this location."
- **macOS (editable):** save location shows current path (default: "~/Downloads/Tether/"); disclosure affordance → system folder picker (Open Panel).
- **Desktop JVM (editable):** save location shows current path (default: "Downloads/Tether/"); disclosure affordance → system folder picker.

**Interactions.**

- Tap save location row (editable platforms): opens system folder picker. On confirmation, path updates in the row.
- Tap save location row (iOS): no action.

**Copy.**

- "File Transfer"
- "Save location"
- "Downloads/Tether/" (or platform-specific default path)
- "On My iPhone → Tether/" (iOS)
- "iOS does not allow changing this location."

**Per-platform deltas.**

- Android: save location editable via the OS folder picker.
- iOS: save location read-only with explanatory caption.
- macOS: save location editable via system folder picker (Open Panel, folder selection mode).
- Desktop JVM: save location editable via system folder picker.

**Accessibility.**

- Save location row (editable): semantic label "Change save location, currently \<path\>".
- Save location row (iOS, read-only): semantic label "Save location: On My iPhone → Tether/. This location cannot be changed on iOS."

---

## Flows

### Flow 1 — In-app send, N files, already paired

1. User opens Tether → DeviceListScreen in populated state. PeerCards in Idle (collapsed).
2. User taps target PeerCard body.
3. **Android/iOS:** MobilePickerChooserSheet appears. User taps "Photos" or "Files". System picker opens. User selects files. Sheet closes.
   **macOS/Desktop:** System file dialog opens directly. User selects files.
4. If selection exceeds threshold (>500 files OR >2 GB): LargeSelectionConfirmDialog appears. User taps [Send button].
5. PeerCard transitions to Active outbound. SendProgressScreen appears (push on mobile).
6. `•—•` fills left-to-right. Filename, progress copy ("X.X MB of Y.Y MB"), and speed update live. Receiver's same PeerCard transitions to Active inbound.
7. Transfer completes. `•—•` plays success animation. SendProgressScreen transitions to TransferSummaryScreen ("Sent \<N\> files to \<peer\>."). Sender's PeerCard transitions to Sent state.
8. User taps [Done button] → DeviceListScreen. Receiver's PeerCard is in Received state: "Received \<N\> files from \<peer\> — tap to open". Persistent until receiver taps [Dismiss × button].

### Flow 2 — Share-sheet entry, already paired

1. User is in Photos / Files app. Taps Share → Tether.
2. Tether opens at DeviceListScreen with pending-outbound banner: "Ready to send \<N\> files (\<size\>). Pick a device below." PeerCards visible in Idle state.
3. User taps target PeerCard body.
4. If selection exceeds threshold: LargeSelectionConfirmDialog. Otherwise: proceeds directly.
5. PeerCard transitions to Active outbound. SendProgressScreen appears. Then TransferSummaryScreen — same as Flow 1 from step 7.

### Flow 3 — Auto-send ON, one peer online (share-sheet entry)

1. User taps Share → Tether (or drags files onto the Tether window on macOS/Desktop). Per-peer auto-send toggle for the sole online peer is On.
2. DeviceListScreen opens. Pending-outbound banner does not pause on device selection — PeerCard transitions immediately to Active outbound. SendProgressScreen appears.
3. Transfer begins without user tapping a PeerCard.
4. SendProgressScreen, then TransferSummaryScreen.

### Flow 4 — First-time auto-send discovery via PeerCard expansion

1. User opens Tether with one paired peer online.
2. User taps the chevron `▾` on that PeerCard → card expands to Idle (expanded).
3. User sees per-peer auto-send toggle (Off by default) and [i] info icon.
4. User taps [i] info icon → tooltip/popover: "Tether will skip the device list and send straight to \<peer\> when no other paired devices are online."
5. User flips toggle to On → preference saved immediately. No confirm dialog. No strip or popup anywhere else. Next share-sheet arrival with this peer as sole online peer will auto-send.

### Flow 5 — Partial batch failure and retry

1. Transfer completes with some file failures (per-file errors during send).
2. SendProgressScreen transitions to TransferSummaryScreen.
3. TransferSummaryScreen shows: "\<X\> of \<Y\> files sent. \<K\> failed." with [Show details button].
4. User taps [Show details button] → list of failed filenames expands inline; button becomes [Hide details button].
5. User taps [Retry failed files button] (peer still online) → SendProgressScreen re-appears for only the un-received files.
6. If retry succeeds: TransferSummaryScreen shows "Sent \<K\> files to \<peer\>." (only the retried batch).
7. If peer went offline: [Retry failed files button] is disabled; helper text "\<peer\> is no longer reachable" shown below button.

### Flow 6 — Cancel mid-transfer (sender)

1. Transfer is in progress. Sender's PeerCard is in Active outbound; SendProgressScreen (if open) is visible.
2. Sender taps [Cancel button] on the PeerCard OR on SendProgressScreen.
3. Both sides stop immediately. No confirm dialog.
4. Files already received in full on the receiver stay on the receiver.
5. Sender's PeerCard transitions to Cancelled state: "Cancelled. \<X\> files were received before cancel. \<Y\> were not sent." [Show received files button] available.
6. Receiver's PeerCard transitions to Received state (partial-completion variant): "Received \<X\> files from \<peer\> — sender cancelled."
7. Both states persist until [Dismiss × button] is tapped.

### Flow 7 — Cancel mid-transfer (receiver)

1. Receiver's PeerCard is in Active inbound.
2. Receiver taps [Cancel button] on PeerCard.
3. Both sides stop immediately.
4. Files already received in full stay on the receiver.
5. Receiver's PeerCard transitions to Received state (partial-completion variant): "Received \<X\> files from \<peer\> — you cancelled."
6. Sender's PeerCard (and SendProgressScreen if open) transitions to Cancelled state: "Sent \<X\> of \<Y\> files to \<peer\> (transfer was cancelled)".

### Flow 8 — Connection lost mid-transfer

1. Transfer is in progress. Wi-Fi drops or peer becomes unreachable.
2. PeerCard (and SendProgressScreen if open) transitions to Connection paused / reconnecting state: "Reconnecting to \<peer\>… (\<countdown\>s)". Countdown from 15 seconds.
3. If connection restores within 15 s: transfer resumes silently. PeerCard returns to Active outbound / Active inbound.
4. If 15 s elapse without reconnection: PeerCard transitions to Error state with appropriate error copy.
5. User taps [Retry button]: re-initiates transfer to the same peer (if peer is reachable). Returns to Active outbound / in-progress state.
6. User taps [Done button] (on SendProgressScreen error) or [Dismiss × button] (on PeerCard error): returns to / stays on DeviceListScreen. PeerCard in Error state persists until dismissed.

### Flow 9 — iOS foreground suspension during inbound transfer

1. Inbound transfer is in progress on receiver's iOS device. Receiver's PeerCard is in Active inbound.
2. User locks screen or OS suspends Tether.
3. Transfer dies. No completion notification fires. Partial file discarded.
4. User brings Tether to foreground.
5. Receiver's PeerCard transitions to iOS-suspension Error variant: "Transfer from \<peer\> was interrupted. Ask \<peer\> to send again." [Dismiss × button] only (no [Retry button]).
6. Sender's PeerCard (and SendProgressScreen if open) transitions to Error state: "\<peer\> is no longer reachable. Try again."

### Flow 10 — Drag-and-drop onto Tether window (macOS / Desktop)

1. User drags file(s) from Finder / File Explorer onto the Tether window.
2. DeviceListScreen shows pending-outbound banner: "Ready to send \<N\> files (\<size\>). Pick a device below."
3. User clicks target PeerCard body.
4. No picker sheet — files already selected. If threshold exceeded: LargeSelectionConfirmDialog. Otherwise: PeerCard transitions to Active outbound; SendProgressScreen fills main content area.

### Flow 11 — Minimize and return to SendProgressScreen

1. Transfer is in progress. User is on SendProgressScreen.
2. User taps minimize button ▾ (or back gesture on Android/iOS).
3. DeviceListScreen is shown. The sender's PeerCard is in Active outbound state — transfer continues.
4. User taps the Active outbound PeerCard body.
5. SendProgressScreen re-opens for that transfer.

---

## Navigation

**DeviceListScreen** is the root screen. It is never replaced — it is always beneath any other screen in the stack.

**MobilePickerChooserSheet** is a bottom-sheet modal overlaid on DeviceListScreen (Android/iOS only). Dismissing it returns focus to DeviceListScreen without navigating anywhere.

**LargeSelectionConfirmDialog** is a modal dialog. It can appear over DeviceListScreen (if triggered from the picker sheet or from drag-drop) or over SendProgressScreen if selection happened late in a flow. Dismissing returns to the triggering context.

**SendProgressScreen** is pushed onto the stack (replaces DeviceListScreen in the visual stack on mobile; on Desktop/macOS it fills the main content area). Back navigation = minimize (returns to DeviceListScreen without cancelling). Cancel = explicit [Cancel button] only.

**TransferSummaryScreen** is pushed after SendProgressScreen (replaces it). [Done button] or back returns to DeviceListScreen (pops back to root, not to SendProgressScreen). The "Retry failed files" path pushes a new SendProgressScreen instance.

**SettingsSection — File Transfer** lives within the existing settings navigation surface. It does not introduce a new navigation root.

**PeerCard** is not a navigable destination — it is an inline component within DeviceListScreen's scrollable list. Its state transitions do not involve navigation stack changes.

---

## Platform notes — Sleep and wake lock

### Android

- Receive-side runs in a foreground service which holds an existing wake-lock (see `docs/knowledge/android-fgs.md`).
- Sender-side wake-lock during active outbound: see Open UX questions item 7.

### iOS

- iOS cannot prevent system sleep or screen-lock. The foreground-only constraint is already documented (see spec Platform notes — iOS). No additional wake-lock design required.

### macOS

- Active transfer (both send and receive) holds an OS sleep-prevention assertion for the duration of the transfer. The assertion is released on completion, cancellation, or error.

### Desktop JVM (Windows / Linux)

- Best-effort wake-lock via available platform APIs. Coverage varies by OS and JVM environment.

---

## Conceptual components

1. **PeerCard** — inline card within DeviceListScreen's peer list; a state machine covering nine states (Idle collapsed, Idle expanded, Active outbound, Active inbound, Connection paused/reconnecting, Received, Sent, Error, Cancelled); the single surface for all per-peer interaction.
2. **PeerCard auto-send toggle** — per-peer toggle with [i] info affordance; lives in PeerCard Idle (expanded); drives the auto-send preference for that specific peer.
3. **Transfer progress mark** — the `•—•` brand mark in transfer-progress state (line fills left-to-right). Used in PeerCard Active states and SendProgressScreen.
4. **Transfer success mark** — the `•—•` in success state (~700 ms animation). Used in PeerCard Received/Sent states, SendProgressScreen, TransferSummaryScreen.
5. **Transfer error mark** — the `•—•` in error state (line truncated, right dot hollow in error tone). Used in PeerCard Error state, SendProgressScreen, TransferSummaryScreen.
6. **Transfer reconnecting mark** — the `•—•` in its Searching state (hollow right dot, opacity oscillation). Used in PeerCard Connection paused/reconnecting state and SendProgressScreen reconnecting state to indicate the app is searching for the peer again.
7. **Pending-outbound banner** — non-dismissible strip above peer-cards; persistent until peer chosen or [Cancel button] tapped; no self-dismiss.
8. **iOS foreground constraint banner** — persistent non-dismissible system-style banner informing the user to keep Tether open during transfers; iOS only.
9. **Current-file label** — one-line center-truncated filename display. Used on PeerCard Active states and SendProgressScreen.
10. **Progress and speed label pair** — "X.X MB of Y.Y MB" and "3.2 MB/s" shown together; always both visible during active transfer.
11. **Skip-count badge** — muted-tone secondary badge showing running file-skip count. Used on PeerCard Active outbound and SendProgressScreen.
12. **Picker chooser sheet (mobile)** — bottom sheet with three tappable source options (Photos / Files / Folder). Android + iOS only.
13. **Large-selection confirm dialog** — destructive-default modal dialog with real file count and size; "Don't show again" checkbox; default focus on [Cancel button]. Supersedes the former folder-only variant.
14. **Transfer summary panel** — end-of-batch result display with expandable failure list and retry affordance. Shown on TransferSummaryScreen.
15. **Retry-failed-files button** — primary button that is disabled (with peer-gone helper text) when the peer has left mDNS.
16. **Inline partial-completion detail** — expandable [Show received files button] / [Hide received files button] toggle within PeerCard Cancelled / Sent / Received (partial) states; lists filenames that were confirmed received.
17. **Deep-link failure hint** — inline platform-specific copy that appears within PeerCard Received state when the OS deep-link to the saved folder fails.
18. **SendProgressScreen minimize affordance** — ▾ button in top bar leading position; minimize-not-cancel; labeled distinctly from [Cancel button].
19. **macOS/Desktop window-close transfer warning** — sheet attached to the window when the user closes Tether mid-transfer.
20. **Settings save-location row** — editable (with system folder picker disclosure) on Android/macOS/Desktop; read-only with explanatory caption on iOS.

---

## Open UX questions

These are non-blocking unless noted. None gate the current implementation unless marked otherwise.

1. **Receiver-side retry for batch failures.** Sender-side retry is in scope for this feature; receiver-initiated retry is deferred. A receiver-side retry — where the receiver itself requests the sender to re-send only the failed files after freeing space — requires a pull-protocol shape, a decision on how long the failed-batch reference persists, and whether the sender must still be online. Deferred until the sender-side retry ships and usage signals the demand.

2. **Mobile picker unification — the "two taps" gap.** The MobilePickerChooserSheet adds one tap to the vision's "two taps to send" on Android and iOS, caused by OS constraints (the OS file/folder pickers do not support mixing folder and multi-file selection in a single picker session). Revisit once OS support evolves or an in-app picker becomes viable.

3. **iOS deep-link to Files app reliability.** The inline hint in PeerCard Received state ("Open Files → On My iPhone → Tether") is a static instruction when the deep-link fails. If the deep-link proves unreliable in practice, a more guided in-app flow may be needed. Monitor failure rate post-launch.

4. **Receiver-cancel copy symmetry.** Flow 7 uses "Received \<X\> files from \<peer\> — you cancelled." for the receiver's own Received partial-completion state. This is a judgment call — an alternative is "…— transfer ended early." which is less accusatory. Confirm preferred framing before implementation.

5. **Edit pending files.** When a pending outbound exists, a future enhancement is to allow adding/removing files from the pending selection before tapping a peer. Currently the selection is locked once picked. Deferred — not in MVP scope.

6. **LargeSelectionConfirmDialog re-enable path.** Once a user ticks "Don't show again" and confirms, there is no in-feature way to re-enable the dialog. The suppression is persistent until app reset. If Settings gains a dedicated file-transfer section expansion in a future feature, a re-enable toggle could live there. For now: one-way suppression, no in-feature re-enable path. Acknowledge this in any onboarding documentation.

7. **Sender-side wake-lock parity.** Android receive-side wake-lock is handled by the foreground service. Sender-side wake-lock during active outbound transfer needs verification — confirm coverage exists or open a tracking issue under #195. Cross-platform parity (macOS OS sleep-prevention assertion, Desktop JVM best-effort) also needs engineering verification before the feature ships.
