# File transfer

How the file-transfer subsystem is laid out — engineering counterpart of [`features/file-transfer/spec.md`](../product/features/file-transfer/spec.md) and [`features/file-transfer/ux-brief.md`](../product/features/file-transfer/ux-brief.md). For the related transport-layer choice see [`adr/adr-network-stack.md`](adr/adr-network-stack.md) and the umbrella in [`transport.md`](transport.md) (once it lands).

## Surface model — PeerCard is the sole transfer surface

**All active and terminal transfer state for one peer renders inside that peer's PeerCard, inline in `DeviceListScreen`.** There is no separate `TransferProgressScreen` or `TransferSummaryScreen`. The card swells when a transfer is active and shrinks back to idle when dismissed.

The only separately navigable transfer surface is **`TransferDetailsScreen`** — the per-file drill-down pushed from a PeerCard's `[Show details →]`. It exists because a per-file list with per-row retry / cancel cannot fit inside a card, not because the card is incomplete.

Consequence for navigation: **`RootComponent.Child` carries `DeviceListChild` and `TransferDetailsChild` and nothing else for the transfer subsystem.** No `TransferChild`, no `ReceiverChild`. Dialogs (`MobilePickerChooserSheet`, `LargeSelectionConfirmDialog`, `WindowCloseTransferWarningDialog`) and banners (`PendingOutboundBanner`, `IosForegroundConstraintBanner`) are modals / overlays inside the device-list child, not navigation destinations.

This rule is what drives most other rules below — keep it in mind when adding a new state.

## State ownership

Per-peer transfer state — outbound, inbound, partial-completion, error, cancelled, reconnecting — lives **once per peer** in a single store keyed by `PeerIdentity`. Two components read from it:

- `PeerTransferComponent` renders the per-peer slice inside the PeerCard.
- `TransferDetailsComponent` renders the same slice as a flat per-file list when the user pushes `TransferDetailsChild`.

They are **two views over the same state**, not two state machines. Per-file retry / cancel actions invoked from `TransferDetailsScreen` mutate the same state the PeerCard observes, so the card reflects them without explicit cross-wiring.

Receiver state is not separate from sender state at the component level. The peer's PeerCard switches between `Active outbound` and `Active inbound` based on direction inside the same `PeerTransferState`; the `FileServer` emits incoming events into the same store the sender writes to.

## Preferences live in two stores, not one

User preferences split by ownership scope, and the split is intentional:

- **Per-peer preferences** (currently: auto-send-when-only-online toggle) live in `PeerPreferencesStore`, keyed by `PeerIdentity`. Surfaced in PeerCard `Idle (expanded)`. Tied to the peer's lifecycle — forgetting / unpairing a peer drops its preferences.
- **App-wide file-transfer preferences** (save location, large-selection-warning enabled) live in `FileTransferPreferences`. Surfaced in `SettingsSection — File Transfer`. Not tied to any peer.

Do not collapse them into one store: the lifetimes and clearing semantics differ. A new file-transfer preference picks one home or the other based on whether forgetting a peer should drop it.

## Retry semantics

**Universal rule: a retry never re-sends a file the receiver has already confirmed received.** Whether the retry is invoked at the batch level (PeerCard `[Retry]`), per-file (TransferDetailsScreen row tap), or whole-set (`[Retry all →]`), the set of files actually sent is the set of files in `Failed` or `Cancelled` status — never `Done`.

This puts the receiver in charge of the source-of-truth on what has and hasn't landed: the sender's `Done` flips only after the receiver acknowledges the file is fully written. Retries therefore re-send only the files the receiver knows it doesn't have.

Per-file `Failed` status carries the failure reason (`Unreadable` on sender, `ConnectionLost`, `ReceiverWriteFailed`, `ReceiverSkipped`, `UserCancelled`, `WholeTransferCancelled`). The reason drives the inline helper copy and whether retry is meaningful at all (`Unreadable` is not retried automatically — sender-side file disappeared / unreadable; surfaced for user awareness).

## Reconnection window

When the connection drops without a graceful end (neither side tapped Cancel), the peer's `PeerTransferState` transitions to `Reconnecting` with a countdown of `RECONNECTION_TIMEOUT` (15 s). If the connection restores within the window the state silently resumes the previous `Active outbound` / `Active inbound`; if the window elapses the state becomes `Error` with the appropriate matrix copy.

The timeout is a single common constant, not a per-platform value. Each disconnect starts a fresh countdown.

## Soft selection threshold

`LargeSelectionConfirmDialog` triggers when a selection exceeds **either** `>500` files **or** `>2 GB`. The threshold lives as a single common constant (`SoftThreshold`) used by every entry point — in-app picker, share-sheet, drag-drop, folder selection. Platforms do not invent their own thresholds.

The "Don't show again" checkbox in the dialog is two-way bound to `FileTransferPreferences.largeSelectionWarningEnabled` — turning it on in the dialog flips the setting; flipping the setting back on in Settings restores the dialog.

## Wake-lock parity

Both sender and receiver hold the strongest sleep-prevention mechanism the platform offers, for the duration of the transfer. A common `WakeLockHolder` interface exposes acquire / release; per-platform actuals decide the mechanism:

- **Android:** the foreground service (`TetherForegroundService`) covers receive-side via FGS + Doze exemption; send-side adds `Window.FLAG_KEEP_SCREEN_ON` and a partial `WakeLock` for the transfer duration.
- **iOS:** suppresses `UIApplication.shared.isIdleTimerDisabled` (auto-lock) during active foreground transfer. Backgrounding or manual lock still ends the session — surfaced via the persistent `IosForegroundConstraintBanner`.
- **macOS:** holds an `IOPMAssertionCreateWithName` of type `kIOPMAssertionTypeNoIdleSleep` for the transfer duration.
- **Windows:** holds `SetThreadExecutionState(ES_SYSTEM_REQUIRED | ES_CONTINUOUS)`.
- **Linux:** requests a `logind`-style inhibit (`org.freedesktop.login1.Manager.Inhibit`). Best-effort on non-systemd setups.

The holder is acquired on the first `Active` transition and released on the last `Terminal` transition — refcounted to handle parallel transfers without re-acquiring or releasing prematurely.

## Folder-send wire contract

Folder send preserves path structure end to end. Sender ships each file with a POSIX-style relative path; receiver `FileServer` sanitizes it (rejects `..`, absolute paths, drive-letters, URL-encoded traversal, null-byte) and lands the file via a platform-specific `UploadStorage` actual. Receiver never trusts sender-supplied paths — see [`adr/adr-channel-encryption.md`](adr/adr-channel-encryption.md) for the broader trust model.

`UploadStorage` is the seam: JVM uses `java.nio.file` rooted at the configured save path; Android uses MediaStore (API 29+, public `Downloads/Tether/...`); Apple uses bookmarks resolved against the per-platform save location.

## Source of truth

When this doc disagrees with [`features/file-transfer/ux-brief.md`](../product/features/file-transfer/ux-brief.md) on interaction surface (where state renders, which dialogs exist, what copy says), the UX brief wins. When this doc disagrees with the code on whether a current class implements the rule, the rule wins — fix the code, not the rule.
