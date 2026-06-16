# UX brief — File transfer

**Spec:** [spec.md](spec.md)
**Status:** `ready`

---

## Information architecture

This feature introduces four new screens / dialogs plus one settings section, and extends the PeerCard component (baseline owned by [device-list/ux-brief.md](../device-list/ux-brief.md)) with transfer-active states. All per-peer transfer state — including in-progress transfers — is contained within PeerCard. The card is the sole transfer surface; there is no separate full-screen progress view.

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
      ├── [tap peer, busy (active/reconnecting), pending outbound exists]
      │       (inert tap; pending selection preserved;
      │        banner switches to busy-peer variant)
      │
      └── [tap peer, idle, no pending outbound] ─────────────────┐
                                                                  ▼
                                          Picker-mode chooser sheet (Android + iOS only)
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

Screens introduced: LargeSelectionConfirmDialog, TransferDetailsScreen, picker-mode chooser sheet, SettingsSection — File Transfer.

Screens touched: DeviceListScreen — extended with file-transfer banners and PeerCard state extensions (baseline owned by [device-list/ux-brief.md](../device-list/ux-brief.md)).

---

## Screens

### DeviceListScreen (file-transfer contributions)

DeviceListScreen is owned by [device-list/ux-brief.md](../device-list/ux-brief.md) — that brief specifies its top bar, banner stack region, PeerCard list, sort, searching/populated states, row transitions, and per-platform navigation chrome. This section names only what file-transfer contributes to the screen.

**Entry points contributed by file-transfer.** OS share-sheet / "Open with" routing Tether to foreground with files pre-selected; drag-and-drop files onto the Tether window (macOS/Desktop). Both arrival paths set a pending-outbound state on the screen.

**Banners contributed to the banner stack region.**

- **iOS foreground constraint banner** (iOS only, present during any active transfer): persistent, non-dismissible. Copy: "Keep Tether open to complete the transfer."
- **Pending-outbound banner** (present only when files are queued but no peer chosen yet): renders below the iOS constraint banner when both are shown. Copy: "Ready to send \<N\> files (\<size\>). Pick a device below." with [Cancel button] on the right. No self-dismiss.
  - **Busy-peer variant** (rendered in place of the default copy when the pending selection is still held AND any of: the user just tapped a PeerCard that is currently in Active outbound, Active inbound, or Connection paused / reconnecting state; or Flow 3 auto-send was suppressed because the sole online paired peer is in one of those states on share-sheet / drag-drop arrival): "\<peer\> is busy with another transfer. Your \<N\> files (\<size\>) are still ready — tap \<peer\> again when it's done, or pick a different device." [Cancel button] stays available on the right. When the busy peer's transfer reaches a terminal state (Sent / Received / Error / Cancelled) the card does NOT return to Idle — it sits in the terminal state until the user dismisses it. The banner accordingly switches to the **terminal-display variant** described next.
  - **Terminal-display variant** (rendered when the pending selection is still held AND any of: the user just tapped — or is still holding pending against — a PeerCard that is in a terminal state Sent / Received / Error / Cancelled; or Flow 3 auto-send was suppressed because the sole online paired peer is in one of those terminal states on share-sheet / drag-drop arrival): "\<peer\>'s last transfer is still showing. Tap × on \<peer\>'s card to dismiss it, then tap \<peer\> again — or pick a different device." [Cancel button] stays available on the right. The banner reverts to the default copy as soon as the user dismisses the terminal state on that PeerCard or taps a different idle peer.

**State overlays on the populated list.**

- **iOS foreground constraint:** banner persists while any transfer is active; disappears automatically when no transfer is active.
- **Per-tap behaviour when pending outbound exists** (Idle / busy / terminal / Received peer states): see § Interactions below.

**Interactions contributed by file-transfer.**

- Tap pending-outbound banner [Cancel button]: clears pending selection, dismisses the banner. No confirm dialog. Available in both default and busy-peer banner variants.
- Tap PeerCard body when pending outbound exists and the peer is Idle: initiates send to that peer. On any platform: first shows LargeSelectionConfirmDialog if threshold exceeded, then transitions card to Active outbound.
- Tap PeerCard body when pending outbound exists and the peer is busy (Active outbound / Active inbound / Connection paused / reconnecting): no card-state change; pending selection preserved; banner switches to (or re-asserts) the busy-peer variant naming this peer. Tapping the busy card's own [Cancel button] (the in-card transfer cancel) is unaffected and still cancels the in-flight transfer per PeerCard.
- Tap PeerCard body when pending outbound exists and the peer is in a terminal state (Sent / Received / Error / Cancelled): no card-state change; pending selection preserved; banner switches to (or re-asserts) the terminal-display variant naming this peer. The card's in-place affordances ([Dismiss ×], [Retry] on Error, [Show details →], Received-state deep-link via the card body's Received-specific tap target) continue to work; only the "tap the empty card body to start a new send" semantics are suppressed while a terminal state is on display. Tapping [Dismiss ×] returns the card to Idle and the banner to default copy; the next tap on the card then initiates the send.
- Tap PeerCard body when no pending outbound exists: on Android/iOS, opens the picker-mode chooser sheet. On macOS/Desktop, opens system file dialog. (This extends device-list's "tap reachable PeerCard → file-send flow" hand-off.)
- All other PeerCard interactions: handled within the card itself (see PeerCard below).

**Copy contributed by file-transfer.**

- "Ready to send \<N\> files (\<size\>). Pick a device below."
- "\<peer\> is busy with another transfer. Your \<N\> files (\<size\>) are still ready — tap \<peer\> again when it's done, or pick a different device." (pending-outbound banner, busy-peer variant)
- "\<peer\>'s last transfer is still showing. Tap × on \<peer\>'s card to dismiss it, then tap \<peer\> again — or pick a different device." (pending-outbound banner, terminal-display variant)
- "Keep Tether open to complete the transfer." (iOS only)

**Per-platform deltas contributed by file-transfer.**

- Android: share-sheet entry sets pending state and shows banner.
- iOS: persistent iOS foreground constraint banner during any transfer. Share-sheet entry sets pending state.
- macOS: drag-and-drop onto the Tether window sets pending state and shows banner; the drop is accepted at the window root on any screen, and the banner surfaces on DeviceListScreen beneath whatever screen is on top. System file dialog replaces the picker-mode chooser sheet. No foreground constraint banner.
- Desktop JVM: same as macOS for drag-and-drop and file dialog. No share-sheet. No foreground constraint banner.

**Accessibility contributed by file-transfer.**

- Pending-outbound banner is a live region (assertive); announces once when it first appears and when content changes. Switching between the default, busy-peer, and terminal-display variants counts as a content change and is re-announced. A repeat tap on the same already-busy or already-terminal PeerCard also re-announces the matching copy so the user gets feedback that their tap was received even though no transfer started.
- After a no-op tap on a busy PeerCard or on a terminal-state PeerCard (with pending outbound held), keyboard focus (Desktop/macOS) stays on that PeerCard — focus does not jump to the banner. The banner's content-change announcement is the user-visible feedback; focus relocation would be disorienting.
- [Cancel button] semantic label: "Cancel pending transfer".
- iOS foreground constraint banner: role is `alert`; announced once when it appears.

---

### PeerCard

**Purpose.** The single point of interaction with a remote peer — covers idle browsing, per-peer settings, all transfer progress (both outbound and inbound), and all post-transfer outcomes. It is not a separate screen; it renders inline within DeviceListScreen's scrollable list.

**Entry points.** Always present on DeviceListScreen for each known peer. State transitions happen in response to user actions and network events.

**Layout (state-dependent — see States below).** In every state the card carries the peer name and current status. The card "swells" (grows in height) when a transfer is active or when expanded.

**States.**

#### 1. Idle (collapsed)

The card's resting state. Layout — peer name, status indicator ("Online" / "Paired — offline" / etc.), peer-identity accent for paired peers — is owned by [device-list/ux-brief.md](../device-list/ux-brief.md) (row variants, Cases 1–4). File-transfer contributes one additional trailing affordance: a chevron `▾` (icon only, not a button label) that expands the card to Idle (expanded) below.

#### 2. Idle (expanded)

The card expanded to reveal per-peer settings.

- Same idle row at top (per device-list); chevron rotates to `▴`.
- Inline block beneath the row:
  - Per-peer auto-send toggle: label "Auto-send to this device when it's your only online device"; description "Sends immediately — no device-list tap required." Toggle control (On/Off).
  - [i] info icon button beside the label: tap → tooltip (or popover on Desktop/macOS): "Tether will skip the device list and send straight to \<peer\> when no other paired devices are online."

#### 3. Active outbound (sending)

Card swells. Transfer in progress from this device to the peer.

- Peer name (top row).
- Progress bar advancing proportionally to total bytes transferred.
- Current filename (one line, center-truncated with ellipsis).
- Progress copy: "X.X MB of Y.Y MB".
- Transfer speed: "3.2 MB/s" (shows "Calculating…" for first ~3 s).
- **Preparing.** Some files (e.g. photos picked from the gallery) need a brief preparation before sending. While a file is being prepared the progress bar is indeterminate and the speed slot reads "Preparing…" instead of "Calculating…"; it switches to the normal proportional bar once the file starts sending.
- Both progress copy and speed are always shown — not one or the other.
- Skip-count badge (appears only when ≥1 file skipped): muted-tone secondary badge: "\<N\> files skipped".
- A [Show details →] button opens TransferDetailsScreen in its in-progress mode (live view of files-received-so-far). Optional drill-down for large batches; the card remains the primary surface.
- [Cancel button] in the trailing position (explicitly a button, visually distinct, labeled "Cancel").
- Tap on the card body (anywhere other than [Cancel button] / [Show details →]) is inert in this state. If a pending outbound exists, the tap is acknowledged via the pending-outbound banner's busy-peer variant (see DeviceListScreen § Banners and § Interactions) — no card-state change, pending selection preserved verbatim.

#### 4. Active inbound (receiving)

Symmetric to Active outbound. Card swells.

- Peer name (top row, prefixed: "From \<peer\>").
- Progress bar.
- Sender's current filename (center-truncated).
- Progress copy: "X.X MB of Y.Y MB".
- A [Show details →] button opens TransferDetailsScreen in its in-progress mode.
- Transfer speed: "3.2 MB/s".
- [Cancel button] in the trailing position.
- Tap on the card body (anywhere other than [Cancel button] / [Show details →]) is inert in this state. If a pending outbound exists, the tap is acknowledged via the pending-outbound banner's busy-peer variant — no card-state change, pending selection preserved verbatim.

#### 5. Connection paused / reconnecting

Triggered when the underlying connection drops without a graceful end (neither side tapped Cancel). Applies symmetrically to both outbound and inbound.

- Peer name.
- Animated searching indicator — semantically: we are searching for the peer again.
- Copy: "Reconnecting to \<peer\>… (\<countdown\>s)".
- Countdown ticks down from `RECONNECTION_TIMEOUT` (default 15 s; final value set at implementation time).
- If connection is restored within the window: card silently resumes the Active outbound or Active inbound state.
- If `RECONNECTION_TIMEOUT` elapses without reconnection: card transitions to Error state.
- Each disconnect starts a fresh `RECONNECTION_TIMEOUT` countdown. A successful reconnect followed by a new disconnect restarts the timer from zero.
- No user action required or available in this state (no Cancel — user cannot cancel a reconnecting transfer; they can wait or the system will timeout to Error).
- Tap on the card body is inert. If a pending outbound exists, the tap is acknowledged via the pending-outbound banner's busy-peer variant — no card-state change, pending selection preserved verbatim. Once this state resolves (back to Active outbound/inbound, or forward to Error), pending-outbound tap semantics follow that successor state.

#### 6. Received (inbound complete)

Replaces any inline inbound card after a successful inbound transfer completes. Persistent — does not self-dismiss.

- Peer name.
- Brief success affirmation, then the progress bar settles fully filled.
- Copy: "Received \<N\> files from \<peer\> — tap to open".
- A [Show details →] button navigates to TransferDetailsScreen for the per-file breakdown (every file shown Done).
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
- Brief success affirmation, then settles.
- Copy: "Sent \<N\> files to \<peer\>".
- A [Show details →] button navigates to TransferDetailsScreen for the per-file breakdown (every file shown Done).
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
- Error indicator (illustration distinct from the progress and searching states).
- Error copy (see error matrix below).
- [Retry button] and [Dismiss × button] in trailing area. [Retry button] is disabled (grayed, not hidden) when the peer is offline.
- A [Show details →] button navigates to TransferDetailsScreen for the per-file outcome breakdown (rows show their final status, even if every row is Failed).

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
- Tap card body (Active outbound / Active inbound / Connection paused / reconnecting): inert for card state. If pending outbound exists, surfaces the pending-outbound banner's busy-peer variant (see DeviceListScreen § Banners and § Interactions); pending selection preserved verbatim.
- Tap card body (Sent / Cancelled / Error) when pending outbound exists: inert for card state — the terminal layout stays put with all its affordances ([Dismiss ×], [Retry] on Error, [Show details →]) reachable. The pending-outbound banner switches to (or re-asserts) the terminal-display variant naming this peer; pending selection preserved verbatim. Card returns to Idle only when the user taps [Dismiss ×], at which point the banner reverts to default copy and the next card-body tap initiates the send.
- Tap card body (Received) when pending outbound exists: the Received-state deep-link-tap behaviour wins — tap attempts the OS deep-link to the saved folder (per Received state above). The card stays in Received; pending selection is preserved verbatim; the pending-outbound banner shows the terminal-display variant naming this peer to remind the user the pending send is still queued. Dismissing the Received state via [Dismiss ×] returns the card to Idle and the banner to default copy; the next card-body tap then initiates the send.
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

- Baseline (focusable container, peer name as heading, status indicator readout, role of the row as `listitem`): owned by [device-list/ux-brief.md](../device-list/ux-brief.md). File-transfer accessibility additions follow.
- Chevron `▾` / `▴`: semantic label "Expand \<peer\> settings" / "Collapse \<peer\> settings".
- Per-peer auto-send toggle: semantic label "Auto-send to \<peer\> when it's the only online device, currently \<On/Off\>".
- [i] info icon: semantic label "More information about auto-send".
- Active outbound / inbound: card is a live region (polite). Announces at three points only (not byte-by-byte):
  - Start: "Sending \<N\> files to \<peer\>" or "Receiving files from \<peer\>".
  - Per-file failure: "Failed to send \<filename\>" (assertive).
  - Done: "Sent \<N\> files to \<peer\>" or "Received \<N\> files from \<peer\>" (assertive).
- Progress bar `contentDescription` in transfer-progress state: "Transfer in progress".
- Status illustration `contentDescription` in success state: "Transfer complete".
- Status illustration `contentDescription` in error state: "Transfer failed".
- Searching indicator `contentDescription` in reconnecting state: "Reconnecting to peer".
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

### Picker-mode chooser sheet

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

- After folder selection (picker-mode chooser sheet on Android/iOS, or system file dialog on macOS/Desktop) when the selection exceeds the threshold.
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
- In-progress mode only: [Cancel transfer button] in the top bar's trailing position — destructive-styled, labeled "Cancel". Tap cancels the transfer immediately (same semantics as [Cancel button] on PeerCard — no confirm dialog). The button disappears when the screen transitions to a terminal layout.
- Aggregate header strip above the list: short summary line — e.g. "12 of 30 sent · 1 failed" — recomputed live; one row, no chrome.
- Scrollable **flat list** of file rows in the order returned by the OS picker (Tether does not resort). Each row has a stable position throughout the transfer; only its trailing status indicator changes as the file's state changes.
- Sender-side, when ≥1 file is in **failed** status: a [Retry all →] CTA below the aggregate header strip re-sends every failed file.

**Per-row anatomy.**

Each row is a single component with a stable layout across all statuses:
- Leading: peerIdentity accent (consistent with DeviceListScreen's paired-peer accent).
- Filename (one line, center-truncated with ellipsis) + size beneath in muted tone (size shown for all statuses except files that never began transferring, where size may not be known — in that case omit).
- Trailing slot: the **status indicator** (one of):
  - **Queued** — clock glyph; muted tone. Sender-side: small [× button] adjacent (skip this file before it starts).
  - **In progress** — small inline progress bar or ring; shows live byte progress for the active file. Only one file is in this state at any moment per transfer direction. Sender-side: small [× button] adjacent (stop this file; sender moves on to next queued file).
  - **Done** — checkmark glyph in success tone.
  - **Failed (sender)** — error glyph; the row also becomes the [Retry button] (the row's trailing area is tappable) with helper text inline when retry is unavailable ("\<peer\> is offline"). When the file is in Failed because the user cancelled it (per-row [× button]), the inline helper reads "Cancelled by you" instead of an error reason; [Retry] remains available.
  - **Failed (receiver)** — error glyph only; no retry affordance (receiver cannot initiate). No per-row cancel on the receiver in MVP (see Open UX questions).
  - **Retrying** — sub-state of in-progress, triggered by per-row or [Retry all →] action; visually identical to In progress.

**States.**

- **In-progress:** the row of the active file shows the In progress trailing indicator with live byte progress; queued files show Queued; finished files show Done. As each file finishes, its trailing indicator transitions Queued → In progress → Done; the row's position in the list does not change. The aggregate header strip recomputes live. When the underlying transfer reaches a terminal state while this screen is open, the screen transitions in-place: no In progress indicator remains, [Cancel transfer button] disappears, failed-row affordances become live. The user is never navigated away involuntarily.
- **Terminal — all success:** every row shows Done. No retry affordances. Aggregate strip: "Received \<N\> of \<N\> files" / "Sent \<N\> of \<N\> files".
- **Terminal — partial:** mix of Done and Failed rows. Sender side: per-row retry on each Failed row + [Retry all →] CTA at top of list. Receiver side: Failed rows are non-interactive (no retry).
- **Terminal — all failed:** every row Failed. Sender side: [Retry all →] CTA visible at top.
- **Loading:** brief indicator while the file list materializes. Rare.

**Interactions.**

- Tap a row with Done status: attempts OS deep-link to that file. Fallback to inline hint within the row if deep-link fails (same platform-specific copy as PeerCard).
- Tap a row with Failed status (sender side, peer reachable): re-initiates transfer of that single file. The row's trailing indicator transitions Failed → In progress (Retrying sub-state). On success the indicator becomes Done; on failure it returns to Failed with the new error inline.
- Tap a row with Failed status when peer is offline: no-op. Inline helper visible: "\<peer\> is offline."
- Tap a row with Queued or In progress status (the row body, not the trailing × button): no action — row body is not interactive in these statuses.
- Tap [× button] on a Queued or In progress row (sender-side only): cancels that single file. The row's status becomes Failed with the inline helper "Cancelled by you"; [Retry] remains available. If the cancelled file was In progress, the sender moves to the next Queued file; otherwise the transfer continues unchanged. The aggregate strip recomputes.
- Tap [Retry all →] CTA: re-initiates transfer for every Failed row. Each row's indicator transitions Failed → In progress → Done (or back to Failed on second failure). Row positions remain stable.
- Tap [Cancel transfer button] (in-progress mode, top bar trailing): cancels the transfer immediately. Screen transitions in-place to the terminal layout. The currently In progress file and every Queued file become Failed with the inline helper "Transfer cancelled" (distinct from "Cancelled by you" which is the per-file × button). Sender-side: [Retry] remains available on each such row; [Retry all →] CTA appears at top. Already-Done rows are unchanged. Partial bytes on the receiver are discarded per the no-partial-file invariant.
- Tap back affordance / hardware back / swipe-back: returns to DeviceListScreen. PeerCard underneath retains its current state (active or terminal).

**Copy.**

- "Sending \<X\> of \<Y\>…" / "Receiving \<X\> of \<Y\>…" (subtitle, in-progress)
- "Received \<X\> of \<Y\> files" / "Sent \<X\> of \<Y\> files" (subtitle, terminal)
- "\<X\> of \<Y\> sent · \<K\> failed" (aggregate header strip; "· \<K\> failed" omitted when K=0)
- Per-row: file name + size (with status icon trailing); size omitted only when never known
- "Retry all" (CTA)
- "Cancel" (top bar trailing button, in-progress mode)
- "Cancelled by you" (Failed-row inline helper when the user cancelled that file via per-row × button)
- "Transfer cancelled" (Failed-row inline helper when the row was interrupted by the whole-transfer cancel)
- "\<peer\> is offline." (failed-row inline helper when peer unreachable)

**Per-platform deltas.**

- Android: hardware back returns to DeviceListScreen. The per-row [× button] hit target meets the Android minimum touch-target size even if the visible × glyph is smaller.
- iOS: swipe-back gesture; top bar back chevron follows iOS HIG. The per-row [× button] hit target meets the iOS minimum touch-target size.
- macOS / Desktop JVM: top bar back affordance rendered as ◀ button. Per-row [× button] hit target sized for comfortable cursor accuracy.

**Accessibility.**

- On screen entry, focus moves to the first list item (or loading indicator).
- Back affordance semantic label: "Back to device list".
- File row is a single focusable element across all statuses (position stable; only the screen-reader status description changes as status evolves — focus and surrounding context do not move).
- File row, status Done: role `button`; semantic label "\<filename\>, \<size\>, received. Activate to open in file manager."
- File row, status In progress: role `text`; status description "in progress, \<percent\> percent".
- File row, status Queued: role `text`; status description "queued".
- File row, status Failed (sender, peer reachable): role `button`; semantic label "\<filename\>, not sent. Activate to retry.".
- File row, status Failed (sender, peer offline): role `text`; semantic label "\<filename\>, not sent. Retry unavailable — \<peer\> is offline.".
- File row, status Failed (sender, user-cancelled this file): role `button`; semantic label "\<filename\>, cancelled by you. Activate to retry.".
- File row, status Failed (sender, whole-transfer cancelled): role `button`; semantic label "\<filename\>, transfer cancelled. Activate to retry.".
- File row, status Failed (receiver): role `text`; semantic label "\<filename\>, not received.".
- [× button] (per-row, Queued / In progress, sender-side): semantic label "Cancel sending \<filename\>".
- [Retry all →] CTA: semantic label "Retry all \<N\> failed files".
- [Cancel transfer button] (in-progress mode): semantic label "Cancel transfer to \<peer\>" / "Cancel transfer from \<peer\>".
- Aggregate header strip: role `text`; updates announced as a live region (polite).
- Live region (polite): announces "Received \<filename\>" as each file completes during in-progress state. Announcements are paced to remain intelligible on fast batches.

---

### SettingsSection — File Transfer

**Purpose.** The settings area where the user configures the save location, whether received media goes to the iOS Photos library, and the large-selection warning. The per-peer auto-send toggle lives in the expanded PeerCard, not here.

**Entry points.** App settings surface (existing) — a dedicated "File Transfer" section within it.

**Layout.**

- Section header: "File Transfer"
- Save location row: label "Save location"; value shows the current path (e.g. "Downloads/Tether/"); on editable platforms, a disclosure affordance (chevron or "Change" link) opens the system folder picker. On iOS: read-only, no disclosure affordance.
- Save-to-Photos toggle row (**iOS only**): label "Save photos & videos to Photos"; toggle control (On by default); caption beneath the label: "Received photos and videos go to your Photos library instead of Files." Sits directly beneath the Save location row — both rows describe where received media lands, and the caption distinguishes the gallery destination from the Files destination named above.
- Large-selection warning toggle row: label "Show large-selection warnings"; toggle control (On by default). When Off, LargeSelectionConfirmDialog is suppressed globally.

Auto-send is configured per-peer via the expanded PeerCard (see PeerCard § Idle expanded).

**States.**

- **Android (editable):** save location shows current path; tap row → system folder picker to change. No save-to-Photos row.
- **iOS (read-only):** save location shows "On My iPhone → Tether/"; no change affordance. A caption beneath: "iOS does not allow changing this location." The save-to-Photos row is present.
- **macOS (editable):** save location shows current path (default: "~/Downloads/Tether/"); disclosure affordance → system folder picker (Open Panel). No save-to-Photos row.
- **Desktop JVM (editable):** save location shows current path (default: "Downloads/Tether/"); disclosure affordance → system folder picker. No save-to-Photos row.
- All platforms: large-selection warning toggle is present and functions identically.

**Save-to-Photos toggle states (iOS).**

- **On (default):** received photos and videos are saved to the Photos library; once the save is confirmed, the file is removed from Files so the media lives only in the gallery. iOS's own add-to-Photos consent prompt is requested at the first inbound media save while the toggle is On — see § Flows. The toggle reflects the user's intent; it does not by itself reflect whether Photos permission has been granted. If the save cannot complete (permission denied, unsupported codec, or save failure), the file stays in Files — it is never lost.
- **Off:** received media stays in `On My iPhone → Tether/` and no Photos permission is requested. Flipping to Off after a grant does not move newly received media — from that point it stays in Files.

The denied-permission recovery caption (a caption variant shown beneath the On toggle when add-to-Photos access has been denied) is tracked with the deferred Photos-permission UX.

**Interactions.**

- Tap save location row (editable platforms): opens system folder picker. On confirmation, path updates in the row.
- Tap save location row (iOS): no action.
- Tap save-to-Photos toggle (iOS): flips On/Off immediately, no confirm dialog. Flipping On does not itself trigger the OS prompt — the prompt fires at the next actual media save (see § Flows). Flipping Off stops future gallery copies; it requests no permission and removes nothing already saved.
- Tap large-selection warning toggle: flips On/Off immediately. When flipped to On, re-enables LargeSelectionConfirmDialog for future large selections.

**Copy.**

- "File Transfer"
- "Save location"
- "Downloads/Tether/" (or platform-specific default path)
- "On My iPhone → Tether/" (iOS)
- "iOS does not allow changing this location."
- "Save photos & videos to Photos" (iOS toggle label)
- "Received photos and videos go to your Photos library instead of Files." (iOS toggle caption)
- "Show large-selection warnings"

**Per-platform deltas.**

- Android: save location editable via the OS folder picker. No save-to-Photos row — received media already lands in a gallery-indexed, user-reachable location.
- iOS: save location read-only with explanatory caption. Save-to-Photos toggle row present, On by default.
- macOS: save location editable via system folder picker (Open Panel, folder selection mode). No save-to-Photos row.
- Desktop JVM: save location editable via system folder picker. No save-to-Photos row.

**Accessibility.**

- Save location row (editable): semantic label "Change save location, currently \<path\>".
- Save location row (iOS, read-only): semantic label "Save location: On My iPhone → Tether/. This location cannot be changed on iOS."
- Save-to-Photos toggle (iOS): semantic label "Save received photos and videos to Photos, currently \<On/Off\>".
- Large-selection warning toggle: semantic label "Show large-selection warnings, currently \<On/Off\>".

---

## Flows

### Flow 1 — In-app send, N files, already paired

1. User opens Tether → DeviceListScreen in populated state. PeerCards in Idle (collapsed).
2. User taps target PeerCard body.
3. **Android/iOS:** the picker-mode chooser sheet appears. User taps "Photos" or "Files". System picker opens. User selects files. Sheet closes.
   **macOS/Desktop:** System file dialog opens directly. User selects files.
4. If selection exceeds threshold (>500 files OR >2 GB): LargeSelectionConfirmDialog appears. User taps [Send button].
5. PeerCard transitions to Active outbound. Progress bar advances. Filename, progress copy ("X.X MB of Y.Y MB"), and speed update live. Receiver's same PeerCard transitions to Active inbound.
6. Transfer completes. Success affirmation plays. Sender's PeerCard transitions to Sent state: "Sent \<N\> files to \<peer\>". Receiver's PeerCard transitions to Received state: "Received \<N\> files from \<peer\> — tap to open". Both states persist until dismissed.

### Flow 2 — Share-sheet entry, already paired

1. User is in Photos / Files app. Taps Share → Tether.
2. Tether opens at DeviceListScreen with pending-outbound banner: "Ready to send \<N\> files (\<size\>). Pick a device below." PeerCards visible in Idle state.
3. User taps target PeerCard body.
4. If selection exceeds threshold: LargeSelectionConfirmDialog. Otherwise: proceeds directly.
5. PeerCard transitions to Active outbound — same as Flow 1 from step 5.

### Flow 2a — Share-sheet entry, target peer is busy with another transfer

1. User taps Share → Tether (or drags files onto the Tether window). Tether opens at DeviceListScreen with the pending-outbound banner (default copy): "Ready to send \<N\> files (\<size\>). Pick a device below."
2. User taps target PeerCard body. That PeerCard is currently in Active outbound, Active inbound, or Connection paused / reconnecting (a transfer with that peer is already in flight).
3. No card-state change. Pending selection stays held verbatim. The pending-outbound banner switches to its busy-peer variant: "\<peer\> is busy with another transfer. Your \<N\> files (\<size\>) are still ready — tap \<peer\> again when it's done, or pick a different device." The banner's content change is announced (assertive live region); keyboard focus stays on the PeerCard.
4. From here, the user has three deterministic paths back:
   - **Wait for the busy peer.** When the in-flight transfer reaches a terminal state (Sent / Received / Error / Cancelled — including post-Reconnecting Error), the PeerCard sits in that terminal state — it does NOT return to Idle on its own. The pending-outbound banner switches from the busy-peer variant to the terminal-display variant: "\<peer\>'s last transfer is still showing. Tap × on \<peer\>'s card to dismiss it, then tap \<peer\> again — or pick a different device." The user taps [Dismiss ×] on that PeerCard; the card returns to Idle and the banner reverts to default copy. The user taps the same PeerCard body again — now Idle — and the original pending-outbound flow proceeds from Flow 2 step 4 (LargeSelectionConfirmDialog if threshold exceeded, else card transitions to Active outbound). The user MAY instead choose to inspect the terminal state first ([Show details →], the Received-state OS deep-link, or [Retry] on Error); those affordances stay reachable while pending is held, and the pending selection is preserved verbatim throughout.
   - **Pick a different idle peer.** User taps any other Idle PeerCard. The pending-outbound banner is dismissed and the send proceeds against the newly-chosen peer per Flow 2 step 4.
   - **Abandon the send.** User taps [Cancel button] on the banner. Pending selection cleared, banner dismissed; busy peer's transfer is unaffected.

### Flow 3 — Auto-send ON, one peer online (share-sheet entry)

1. User taps Share → Tether (or drags files onto the Tether window on macOS/Desktop). Per-peer auto-send toggle for the sole online peer is On.
2. DeviceListScreen opens.
   - **Sole online peer is Idle:** PeerCard transitions immediately to Active outbound without requiring a device-list tap. Transfer begins.
   - **Sole online peer is busy (Active outbound / Active inbound / Connection paused / reconnecting) or in a terminal state (Sent / Received / Error / Cancelled):** auto-send cannot fire — the peer is not Idle, and queueing the new payload to follow the active transfer is out of scope. The screen falls back to the manual pending-outbound flow: the pending-outbound banner appears in the matching variant (busy-peer variant when the peer is busy; terminal-display variant when the peer is in a terminal state) naming the auto-send peer. From here the user follows Flow 2a — wait for the busy peer (and dismiss its terminal state), pick a different idle peer (none available if this is the sole online peer, so the user waits or abandons), or tap the banner's [Cancel button] to abandon the send. Auto-send does not re-fire automatically once the peer becomes Idle: the user must tap the PeerCard to initiate the send. This mirrors the UX the user would have gotten had auto-send been Off.
3. Transfer begins (in the Idle branch). PeerCard shows Active outbound state.

### Flow 4 — First-time auto-send discovery via PeerCard expansion

1. User opens Tether with one paired peer online.
2. User taps the chevron `▾` on that PeerCard → card expands to Idle (expanded).
3. User sees per-peer auto-send toggle (Off by default) and [i] info icon.
4. User taps [i] info icon → tooltip/popover: "Tether will skip the device list and send straight to \<peer\> when no other paired devices are online."
5. User flips toggle to On → preference saved immediately. No confirm dialog. Next share-sheet arrival with this peer as sole online peer will auto-send.

### Flow 5 — Partial batch failure and retry

1. Transfer completes with some file failures (per-file errors during send).
2. Sender's PeerCard transitions to Sent state (partial-completion variant): "Sent \<X\> of \<Y\> files to \<peer\> (\<Z\> files couldn't be read)". [Show details →] button and [Retry button] present. [Dismiss ×] present.
3. User taps [Show details →] → TransferDetailsScreen opens; flat list with each file's final status — Done rows and Failed rows interleaved in OS-picker order. Aggregate strip: "\<X\> of \<Y\> sent · \<Z\> failed".
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

The drop is accepted at the window root — on any screen, not only DeviceListScreen. A drop while TransferDetailsScreen is on top still stages the pending-outbound files; the pending-outbound banner surfaces on DeviceListScreen, which is always beneath any pushed screen.

1. User drags file(s) from Finder / File Explorer onto the Tether window.
2. DeviceListScreen shows pending-outbound banner: "Ready to send \<N\> files (\<size\>). Pick a device below."
3. User clicks target PeerCard body.
4. No picker sheet — files already selected. If threshold exceeded: LargeSelectionConfirmDialog. Otherwise: PeerCard transitions to Active outbound.

### Flow 11 — View transfer details (per-file)

1. A transfer is in any non-Idle state (Active outbound, Active inbound, Sent, Received, Cancelled, Error). PeerCard shows [Show details →] button.
2. User taps [Show details →] → TransferDetailsScreen opens. In active states, the screen renders in in-progress mode; in terminal states, the appropriate terminal layout.
3. Flat list renders in OS-picker order; each row carries a status indicator (Queued / In progress / Done / Failed). Aggregate strip above the list summarises counts. Row positions stay stable as statuses evolve.
4. User taps a Done row → OS deep-link to that file; fallback to inline hint if deep-link fails.
5. In-progress mode (sender-side per-file cancel): user taps [× button] on a Queued or In progress row → that single file is cancelled and becomes Failed with "Cancelled by you"; transfer continues with remaining files. Aggregate strip recomputes.
6. In-progress mode (whole-transfer cancel): user taps [Cancel transfer button] in the top bar → transfer cancels immediately; screen transitions in-place to the appropriate terminal layout (no navigation away).
7. Sender-side: user taps a Failed row → that file is re-sent; row's status transitions Failed → In progress → Done (or back to Failed). [Retry all →] CTA re-sends every Failed file at once. Row positions remain stable.
8. User taps back → returns to DeviceListScreen; PeerCard underneath retains its current state.

### Flow 12 — iOS: receive media to Photos, first-time consent (iOS only)

1. The save-to-Photos toggle is On (default). An inbound transfer carrying ≥1 photo or video completes on iOS. Each received file first lands in Files (`On My iPhone → Tether/`) as the holding location.
2. For the photos and videos in the batch, Tether saves them to the Photos library. Because add-to-Photos access has not been requested before, iOS's own add-to-Photos prompt appears.
3. On the prompt:
   - **Granted:** the photos and videos are saved to Photos; once each save is confirmed, that file is removed from Files so the media lives only in the gallery. The PeerCard's Received state and its [Show details →] breakdown are unchanged — there is no separate "saved to Photos" surface; the move is silent.
   - **Denied:** nothing is saved to Photos; the media stays in Files. No blocking error and no per-transfer alert — the Received state completes normally. The toggle stays On reflecting the user's intent. The denied-permission recovery surface is tracked with the deferred Photos-permission UX.
4. After a grant, subsequent inbound media is moved to Photos silently with no further prompt. After a denial, subsequent inbound media silently stays in Files; Tether does not re-prompt (iOS does not re-show the add-to-Photos prompt once decided) and does not nag. A photo or video whose Photos save fails for any other reason (e.g. unsupported codec) also stays in Files — it is never lost. Non-media files in any batch always stay in Files regardless of the toggle or the permission outcome.

With the toggle Off, none of the above runs: no OS prompt, media stays in Files only — behaviour identical to a Files-only receiver.

A Tether-owned rationale screen that precedes iOS's add-to-Photos prompt is tracked in a follow-up issue.

---

## Navigation

**DeviceListScreen** is the root screen. It is never replaced — it is always beneath any other screen in the stack.

The **picker-mode chooser sheet** is a bottom-sheet modal overlaid on DeviceListScreen (Android/iOS only). Dismissing it returns focus to DeviceListScreen without navigating anywhere.

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

1. **PeerCard** — inline card within DeviceListScreen's peer list; baseline (Idle collapsed row variants Cases 1–4) owned by [device-list/ux-brief.md](../device-list/ux-brief.md). File-transfer extends it with the Idle (expanded) state and the transfer-active states (Active outbound, Active inbound, Connection paused/reconnecting, Received, Sent, Error, Cancelled).
2. **PeerCard auto-send toggle** — per-peer toggle with [i] info affordance; lives in PeerCard Idle (expanded); drives the auto-send preference for that specific peer.
3. **Transfer progress bar** — fills as bytes flow. Used in PeerCard Active states.
4. **Transfer success affirmation** — brief visual moment after completion. Used in PeerCard Received/Sent states.
5. **Transfer error indicator** — illustration distinct from progress and searching. Used in PeerCard Error state.
6. **Transfer reconnecting indicator** — animated searching illustration. Used in PeerCard Connection paused/reconnecting state to indicate the app is searching for the peer again.
7. **Pending-outbound banner** — non-dismissible strip above peer-cards; persistent until peer chosen or [Cancel button] tapped; no self-dismiss. Carries two copy variants asserted on top of the default copy: a **busy-peer variant** when the user taps a PeerCard already in an active or reconnecting state while pending files are held, and a **terminal-display variant** when the user taps (or holds pending against) a PeerCard sitting in a terminal state (Sent / Received / Error / Cancelled) that has not yet been dismissed.
8. **iOS foreground constraint banner** — persistent non-dismissible system-style banner informing the user to keep Tether open during transfers; iOS only.
9. **Current-file label** — one-line center-truncated filename display. Used on PeerCard Active states.
10. **Progress and speed label pair** — "X.X MB of Y.Y MB" and "3.2 MB/s" shown together; always both visible during active transfer.
11. **Skip-count badge** — muted-tone secondary badge showing running file-skip count. Used on PeerCard Active outbound.
12. **Picker chooser sheet (mobile)** — bottom sheet with three tappable source options (Photos / Files / Folder). Android + iOS only.
13. **Large-selection confirm dialog** — destructive-default modal dialog with real file count and size; "Don't show again" checkbox; default focus on [Cancel button]. Applies on all platforms wherever a selection exceeds the threshold.
14. **Per-file row** — single component used for every file in TransferDetailsScreen, position stable in OS-picker order; trailing slot renders one of five status indicators (Queued / In progress / Done / Failed-sender-retryable / Failed-non-retryable).
15. **Aggregate transfer-counts strip** — single-line summary above the per-file list; recomputes live ("\<X\> of \<Y\> sent · \<K\> failed", K-clause omitted when zero).
16. **Retry-failed-files button** — primary button on Error PeerCard that is disabled (grayed) when the peer is offline.
17. **Navigational transfer-details button** — [Show details →] button on PeerCard in Active outbound, Active inbound, Sent, Received, Cancelled, and Error states; opens TransferDetailsScreen.
18. **Per-row retry affordance** — on TransferDetailsScreen, a Failed row (sender-side) is itself tappable to retry that single file; row stays in place, trailing status indicator transitions Failed → In progress → Done (or back to Failed). Disabled (non-tappable, with inline helper "\<peer\> is offline") when peer is offline.
19. **Per-row cancel button** — [× button] adjacent to the trailing status indicator on Queued and In progress rows (sender-side only). Tap cancels that single file; row transitions to Failed with the inline helper "Cancelled by you"; [Retry] remains available.
20. **Retry-all-failed CTA** — [Retry all →] button below the aggregate strip on TransferDetailsScreen when ≥1 Failed row exists; sender-side only.
21. **Deep-link failure hint** — inline platform-specific copy that appears within PeerCard Received state (or within a TransferDetailsScreen file row) when the OS deep-link to the saved folder fails.
22. **macOS/Desktop window-close transfer warning** — sheet attached to the window when the user closes Tether mid-transfer.
23. **Settings save-location row** — editable (with system folder picker disclosure) on Android/macOS/Desktop; read-only with explanatory caption on iOS.
24. **Settings large-selection warning toggle** — toggle in SettingsSection — File Transfer; On by default; re-enables LargeSelectionConfirmDialog after "Don't show again" suppression.
25. **Settings save-to-Photos toggle** — iOS-only toggle in SettingsSection — File Transfer; On by default; carries the move-explanation caption that received media goes to the gallery instead of Files.

---

## Open UX questions

These are non-blocking unless noted. None gate the current implementation unless marked otherwise.

1. **Receiver-side retry for batch failures.** Sender-side retry is in scope for this feature; receiver-initiated retry is deferred. A receiver-side retry — where the receiver itself requests the sender to re-send only the failed files after freeing space — requires a pull-protocol shape, a decision on how long the failed-batch reference persists, and whether the sender must still be online. Deferred until the sender-side retry ships and usage signals the demand.

2. **Mobile picker unification — the "two taps" gap.** The picker-mode chooser sheet adds one tap to the vision's "two taps to send" on Android and iOS, caused by OS constraints (the OS file/folder pickers do not support mixing folder and multi-file selection in a single picker session). Revisit once OS support evolves or an in-app picker becomes viable.

3. **iOS deep-link to Files app reliability.** The inline hint in PeerCard Received state ("Open Files → On My iPhone → Tether") is a static instruction when the deep-link fails. If the deep-link proves unreliable in practice, a more guided in-app flow may be needed. Monitor failure rate post-launch.

4. **Receiver-cancel copy symmetry.** Flow 7 uses "Received \<X\> files from \<peer\> — you cancelled." for the receiver's own Received partial-completion state. An alternative is "…— transfer ended early." which is less accusatory. Confirm preferred framing before implementation.

5. **Edit pending files.** When a pending outbound exists, a future enhancement is to allow adding/removing files from the pending selection before tapping a peer. Deferred — not in MVP scope.

6. **Sender-side wake-lock parity (engineering).** Each platform holds the strongest sleep-prevention mechanism available for the duration of a transfer: Android — FGS covers receive-side, send-side adds screen-keep-on + partial wake-lock; iOS — auto-lock idle-timer suppression (foreground only); macOS — OS sleep-prevention assertion (both directions); Windows — execution-state assertion (send); Linux — inhibit interface (send; best-effort on non-systemd). Engineering verification and implementation tracking is owned by #195.

7. **Receiver-side per-file cancel.** Sender-side per-file cancel is in scope for MVP (× on Queued / In progress rows of TransferDetailsScreen). Receiver-side equivalent — letting the receiver skip an incoming file mid-batch — requires a protocol signal back to the sender ("skip this file, continue") and is out of MVP scope. The receiver still has the whole-transfer Cancel button on PeerCard and on TransferDetailsScreen.
