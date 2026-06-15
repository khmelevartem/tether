# UX brief — Device list

**Spec:** [spec.md](spec.md)
**Status:** `draft` — only the populated-state row contract is worked through here; the searching state, first-launch experience, and other surfaces of the device list still need a dedicated pass before the brief covers the screen end-to-end.

## Information architecture

One screen — DeviceListScreen, the root of the app. This brief specifies the **populated state**: row variants, sort, transitions, interactions, the offline-paired hint. The full-screen no-local-network state and its visual treatment live in [wifi-availability/ux-brief.md](../system/wifi-availability/ux-brief.md); this brief only commits that when the local network is missing, the populated list is taken over by that state. The searching state and first-launch experience are not specified here yet.

## Screens

### DeviceListScreen (populated state)

**Purpose.** The device list when the local device is on a usable network, showing reachable and/or known-but-offline paired peers.

**Entry points.** App launch (root screen). No other entry point.

**Layout.**

- Top bar: app name / title; no action buttons relevant to this brief.
- Banner stack region: a slot directly below the top bar reserved for cross-feature banners contributed by other briefs (e.g. file-transfer's pending-outbound banner and iOS foreground constraint banner; wifi-availability's no-network state replaces the whole screen and does not use this slot). Banners stack vertically; their order, copy, and dismiss semantics are owned by the contributing brief.
- Scrollable list of PeerCards, filling the available screen area below the banner stack (or below the top bar when no banners are present).
- No pinned action at the bottom — actions are row-level.
- If the list contains only offline-paired rows and no reachable peers, the list is still shown — no searching overlay on top.

**Row variants (four cases, one row contract).**

All rows share the same height, internal padding, and typographic hierarchy. The four cases differ only in accent treatment, dimming, and tappability.

**Case 1: Online & unpaired**
- Standard row. Device name (weight 600), platform hint or secondary detail (weight 400, secondary tone).
- No peer-identity accent applied.
- Full opacity.
- Tappable: leads to the file-send flow.
- No hint text.

**Case 2: Online & paired**
- Standard row, structure identical to Case 1.
- Peer-identity accent applied: the warm copper/amber `peerIdentity` hue appears as a contained visual marker on the row — left-edge strip by default (consistent with how identity accent is used elsewhere; `peerIdentity` is never the background, never the text color, always a contained accent signal).
- Full opacity.
- Tappable: leads to the file-send flow.
- No hint text.

**Case 3: Offline & paired**
- Dimmed row. Same structure as Case 2, but the entire row is rendered at reduced opacity (visually recessed). The `peerIdentity` accent is retained at the same reduced opacity — recognisable, but clearly not active.
- Not tappable for sending. Tapping the row surfaces an inline hint (see below) — it does not open the file-send flow.
- Hint text: "Not on this network. Make sure Wi-Fi is on and Tether is running on it."
- Hint is dismissible (tap elsewhere on mobile, click elsewhere on desktop).
- No spinner, no "retry" affordance on the row. Recovery is automatic.
- **Extended by [clipboard transfer](../clipboard-transfer/ux-brief.md):** while this row holds pending received clipboard items, it becomes expandable (gains the expand chevron and unread badge, role "button"), so the receiver can reach those items even after the sender goes offline. With no pending items the row keeps the hint-only, non-button behaviour above. The expandability and chevron are an owned extension by clipboard transfer; see its brief for the source of the addition.

**Case 4: Offline & unpaired**
- Not shown. The row does not appear in the list.

**States (within the populated list).**

- **Mixed (reachable + offline paired):** both row types shown, offline rows sorted below reachable ones.
- **Reachable only:** list of Case 1 / Case 2 rows. No offline section.
- **Offline-paired only** (local on network, no reachable peers): list of Case 3 rows only. No "Searching…" overlay above the list; the list itself is the content.
- **Empty** (local on network, no paired devices, no reachable peers): searching state (existing device-list behaviour) — not respecified here.

**Sort.**

- Reachable peers come first.
- Offline-paired rows come below, sorted **last-seen first** — the device that was most recently online appears at the top of the offline section. Within reachable peers, sort is owned by the spec's Open product questions (discovery order today).

**Transitions.**

- A paired device coming online: its row transitions from Case 3 (dimmed) to Case 2 (full opacity, tappable) in place — no insertion/removal, no position jump. 200–300 ms ease-out fade-in on the dimming.
- A paired device going offline: Case 2 → Case 3 in place, same ease-out.
- An unpaired device disappearing from the network: its row is removed. No animation beyond the list's standard reorder.
- The local device losing its network: the entire list is replaced by NoLocalNetworkState (owned by wifi-availability). No row-level animation required — the screen-level transition replaces everything.
- The local device regaining its network: NoLocalNetworkState → searching state → rows populate as peers are found.

**Interactions.**

- Tap Case 1 / Case 2 row: enter file-send flow (owned by another feature).
- Tap Case 3 row: expand the inline hint. Does NOT enter file-send.
- Long-press (mobile) / right-click (desktop) on any row: opens a context menu or detail sheet. Contents (rename, unpair, etc.) are out of scope for this brief; this brief only requires the affordance exists and is reachable.
- Pull-to-refresh: not provided. Discovery is live; the list updates automatically.
- Keyboard navigation (Desktop): Tab through rows; Enter activates the primary action (file-send for Cases 1–2, hint expansion for Case 3); Context menu reachable via application key or Shift+F10.

**Copy.**

- Offline-paired row hint: "Not on this network. Make sure Wi-Fi is on and Tether is running on it."
- Hint dismiss: no explicit button — tap/click elsewhere dismisses.

**Per-platform deltas.**

- Android: long-press on row → bottom sheet with row actions. Material long-press pattern adapted to Tether's visual language.
- iOS: long-press on row → iOS 13+ context menu.
- macOS: right-click on row → context menu popover.
- Desktop (JVM): right-click or application key → context menu.
- All platforms: tap/click behaviour on Cases 1–2 and Case 3 is identical.

**Accessibility.**

- Case 1 / Case 2 rows: semantic role "button" or list item with activation action. Label: "[Device name], [online / paired]" — e.g. "Artem's MacBook, paired". Screen reader announces tappability.
- Case 3 rows: semantic role "list item" (not button). Label: "[Device name], offline, paired. Tap for hint." Screen reader announces that the row is not available for sending.
- The peer-identity accent is a non-text affordance. It must not be the only signal of pairing state — "paired" must also be conveyed in the semantic label.
- Inline hint (Case 3 tap): announced by screen reader as an alert or live region update.
- Focus order (Desktop): top bar → list rows top to bottom → (if hint is open) hint text → rest of list. Hint text does not trap focus.

## Flows

### Flow 1 — Mixed list of reachable and offline-paired peers

1. Local device is on a usable network. User opens Tether.
2. Within a few seconds, reachable peers appear as Case 1 / Case 2 rows.
3. Below them, any previously-paired devices that are not currently reachable appear as Case 3 rows, sorted last-seen first.
4. User taps a reachable row → file-send flow.

### Flow 2 — Offline-paired row tap and recovery

1. Local on a network. A known paired device is offline (powered off, different network, Tether not running).
2. Device shows as a Case 3 row.
3. User taps the row → inline hint appears.
4. User taps elsewhere → hint dismisses.
5. Paired device comes online (joins the same LAN, starts Tether). Row transitions in place from Case 3 to Case 2 within a few seconds. No user action required.

**Failure:** If the device comes online but the row does not transition within ~10 s, the user has no recovery affordance — pull-to-refresh is not provided; transition is automatic. Acceptable per the spec's recovery budget.

### Flow 3 — Local device loses network

1. List visible with peers (live or offline-paired or both).
2. Local device loses its network.
3. NoLocalNetworkState replaces the entire screen ([wifi-availability/ux-brief.md](../system/wifi-availability/ux-brief.md)).
4. When the local device regains its network, the screen transitions through searching back to the populated list.

## Navigation

DeviceListScreen is the root screen. Row variants and transitions are within-screen — no push navigation, no modals for the row contract itself. The context menu (long-press / right-click) opens an overlay or sheet; its contents are owned by a separate brief.

## Conceptual components

1. **PeerCard (standard)** — the base reachable-peer row (online & unpaired and online & paired share this base). Device name + secondary detail, tappable, leads to file-send flow. PeerCard is the umbrella component name — its baseline (Cases 1–4 row variants here) is owned by this brief; file-transfer extends it with transfer-active states (see [file-transfer/ux-brief.md](../file-transfer/ux-brief.md)).
2. **Peer-identity accent** — a contained visual marker in the `peerIdentity` hue applied to paired PeerCards (Cases 2 and 3) to signal trust / prior relationship. Not interactive; not a text element. The accent persists across every PeerCard extension owned by other features (transfer-active states, expanded settings, post-transfer terminal states) — once a peer is paired, the accent stays visible on its PeerCard in all states.
3. **Dimmed PeerCard** — the offline-paired row variant: same structure as the standard row, rendered at reduced opacity with the peer-identity accent retained.
4. **Offline row inline hint** — an in-place expansion (below the row, non-modal) triggered by tapping a dimmed row. Plain-language hint about the absent peer. Dismisses on tap/click elsewhere.
5. **Row state transition** — the animated in-place dimming/brightening when a row moves between Case 2 and Case 3 (200–300 ms ease-out).

## Implementer layout calls

- **Peer-identity accent placement.** Left-edge strip by default. If row geometry (leading icon, platform list insets) makes a strip awkward, fall back to a small dot or badge adjacent to the device name. Same choice for Cases 2 and 3.
- **Inline hint shape.** Inline expansion below the row by default. Where impractical, fall back to a non-auto-dismissing bottom sheet (mobile) or tooltip (desktop). Transient toasts are not acceptable — the hint must not auto-dismiss.
