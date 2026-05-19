# UX brief — Wi-Fi availability

**Spec:** [spec.md](spec.md)
**Status:** `ready`

## Information architecture

One screen — the device list. This brief specifies two things on it:

1. **NoLocalNetworkState** — a full-screen replacement for the device list when the local device has no usable network. Named state of the device list screen, not a separate destination.
2. **Device-list row contract** — the four row variants (online & unpaired, online & paired, offline & paired, offline & unpaired / not shown) with their visual treatment, copy, and interaction behaviour.

## Screens

### DeviceListScreen (existing — additional states only)

**Purpose.** The single surface where the user sees reachable and known-but-offline peers, or learns why the list is empty.

**Entry points.** App launch (root screen). No other entry point.

---

#### State: NoLocalNetworkState (mobile — Wi-Fi off)

**Purpose.** Replaces the device list entirely when the local device has no usable network. Explains the cause in plain language and offers one direct action.

**Layout.**

- Full-screen centred layout with generous vertical spacing; no list, no search indicator.
- Illustration region: the `•—•` mark in its **disconnected** state ([`ui-brand-mark.md` § Disconnected](../../../../engineering/ui-brand-mark.md)) — both dots filled at full opacity, dashed connecting line. Static, no animation. Visually distinct from the searching state (hollow pulsing right dot).
- Title (prominent, weight 600): "Wi-Fi is off"
- Rationale line (secondary, weight 400): "Turn Wi-Fi on to find devices on your network."
- Primary action button (teal accent, full-width on mobile): "Open Wi-Fi settings"
  - Shown only where the OS provides a stable deep-link to the Wi-Fi settings page. See per-platform deltas for which platforms qualify.
  - Where no stable deep-link is available, the button is absent; the rationale line is replaced by a written instruction (see copy below).

**States (within this state — edge cases).**

- Normal: title + rationale + action button (where available).
- No deep-link available: title + written instruction, no button.

**Interactions.**

- Tapping "Open Wi-Fi settings" launches the OS Wi-Fi settings page and returns focus to Tether when the user navigates back.
- No pull-to-refresh. Recovery is automatic — as soon as the device joins a network, the screen transitions to the searching state without any user action.
- Back gesture / back button: standard platform behaviour (exits the app, since this is the root screen).

**Copy.**

- Title: "Wi-Fi is off"
- Rationale (with action button): "Turn Wi-Fi on to find devices on your network."
- Rationale (no deep-link, replaces above): "Turn Wi-Fi on in the system menu to find devices."
- Action button label: "Open Wi-Fi settings"

**Per-platform deltas.**

- Android: deep-link directly into Wi-Fi settings is stable. Show the button. Default layout.
- iOS: no stable deep-link directly into Wi-Fi settings — `UIApplication.openSettingsURLString` only opens Tether's own app-permissions page, which does not let the user toggle Wi-Fi in one tap. A button that lands the user a tap away from where they expected is worse than no button. Show written instruction, no button.
- macOS: no stable deep-link to Wi-Fi settings from a sandboxed app. Show written instruction, no button.
- Desktop (JVM / Linux): no stable deep-link cross-distro. Show written instruction, no button.

**Accessibility.**

- The `•—•` illustration region: semantic label "No network connection" (hidden from sighted users, announced by screen reader).
- "Open Wi-Fi settings" button: label matches visible text; announces as a button that opens an external app.
- Focus order (Desktop): illustration (skip, decorative) → title → rationale → button (if present).
- The screen must not trap focus; back / close remains reachable via keyboard on Desktop.

---

#### State: NoLocalNetworkState (desktop — no local network)

**Purpose.** Same full-screen replacement, but with neutral wording that does not assume Wi-Fi.

**Layout.** Identical structure to the mobile variant.

- Same disconnected `•—•` illustration.
- Title (prominent, weight 600): "You're not on a local network"
- Rationale line (secondary, weight 400): "Connect to Wi-Fi or Ethernet to find devices."
- Written instruction (replaces rationale when no deep-link): "Connect to Wi-Fi or Ethernet in the system network menu."
- No action button on macOS or Desktop JVM (no stable deep-link). See per-platform deltas.

**States.** Same as mobile variant — normal vs. no-deep-link. On macOS and Desktop JVM, no-deep-link is always the active sub-state.

**Interactions.** Identical to mobile variant. Recovery is automatic.

**Copy.**

- Title: "You're not on a local network"
- Rationale: "Connect to Wi-Fi or Ethernet to find devices."
- Written instruction (no button): "Connect to Wi-Fi or Ethernet in the system network menu."

**Per-platform deltas.**

- Android: not applicable — Android always uses the mobile wording.
- iOS: not applicable — iOS always uses the mobile wording.
- macOS: desktop wording; no action button.
- Desktop (JVM): desktop wording; no action button.

**Accessibility.** Same as mobile variant; substitute the macOS/Desktop label "No local network" for the illustration region.

---

#### State: PopulatedList (row contract)

**Purpose.** The device list when the local device is on a usable network, showing reachable and/or known-but-offline peers.

**Layout.**

- Scrollable list of device rows, filling the available screen area below the top bar.
- Top bar: app name / title, no action buttons relevant to this feature.
- No pinned action at the bottom (actions are row-level).
- If the list contains only offline-paired rows and no reachable peers, the list is still shown — no searching overlay is displayed on top.

**Row variants (four cases, one row contract).**

All rows share the same height, internal padding, and typographic hierarchy. The four cases differ only in accent treatment, dimming, and tappability.

**Case 1: Online & unpaired**
- Standard row. Device name (weight 600), platform hint or secondary detail (weight 400, secondary tone).
- No peer-identity accent applied.
- Full opacity.
- Tappable: leads to the file-send flow.
- No hint text.

**Case 2: Online & paired**
- Standard row. Device name (weight 600), platform hint or secondary detail (weight 400, secondary tone).
- Peer-identity accent applied: the warm copper/amber `peerIdentity` hue appears as a visual marker on the row — as a left-edge strip or a small dot/badge adjacent to the device name. Exact placement: left-edge strip (consistent with how identity accent is used elsewhere in the visual language — `peerIdentity` is never the background, never the text color, always a contained accent signal). Full opacity.
- Tappable: leads to the file-send flow.
- No hint text.

**Case 3: Offline & paired**
- Dimmed row. Same structure as Case 2, but the entire row is rendered at reduced opacity (visually recessed — "dimmed" per the spec contract). The `peerIdentity` accent is retained at the same reduced opacity, so it remains recognisable but clearly not active.
- Not tappable for sending. Tapping the row surfaces an inline hint (see below) — it does not open the file-send flow.
- Hint text (shown on tap, inline below the row or as a non-modal tooltip-style expansion): "Not on this network. Make sure Wi-Fi is on and Tether is running on it."
- The hint is dismissible (tap elsewhere or swipe away on mobile; click elsewhere on desktop).
- The row does not show a spinner or "retry" affordance. Recovery is automatic.
- Sorted: offline paired rows appear below all reachable peers, never interleaved. Within the offline group, rows are sorted **last-seen first** — the device that was most recently online appears at the top of the offline section.

**Case 4: Offline & unpaired**
- Not shown. The row does not appear in the list.

**States (within PopulatedList).**

- Mixed (reachable + offline paired): both row types shown, offline rows sorted below reachable ones.
- Reachable only: list of Case 1 / Case 2 rows. No offline section.
- Offline-paired only (local device on network, no reachable peers): list of Case 3 rows only. No "Searching…" overlay above the list; the list itself is the content.
- Empty (local device on network, no paired devices, no reachable peers): searching state (existing spec) — not this brief's responsibility.

**Transitions.**

- A paired device coming online: its row transitions from Case 3 (dimmed, offline) to Case 2 (full opacity, tappable) in place — no row insertion/removal, no position jump. Transition: 200–300 ms ease-out fade-in on the dimming.
- A paired device going offline: its row transitions from Case 2 to Case 3 in place. Same ease-out.
- An unpaired device disappearing from the network: its row is removed. No animation specified beyond the list's standard reorder.
- The local device losing its network: the entire list is replaced by NoLocalNetworkState. No row-level animation required — the screen-level transition replaces everything.
- The local device regaining its network: NoLocalNetworkState is replaced by the searching state, then rows populate as peers are found.

**Interactions.**

- Tap Case 1 or Case 2 row: enter file-send flow (owned by another feature).
- Tap Case 3 row: expand inline hint. Does not enter file-send flow.
- Long-press (mobile) / right-click (desktop) on any row: opens a context menu or detail sheet. The exact contents of that menu (rename, unpair, etc.) are out of scope for this brief; this brief only requires the affordance exists and is reachable.
- Pull-to-refresh: not provided. Discovery is live; the list updates automatically.
- Keyboard navigation (Desktop): Tab through rows; Enter activates the primary action (file-send for Cases 1–2, hint expansion for Case 3); Context menu reachable via application key or Shift+F10.

**Copy.**

- Offline paired row hint: "Not on this network. Make sure Wi-Fi is on and Tether is running on it."
- Hint dismiss: no explicit button needed — tap/click elsewhere dismisses.

**Per-platform deltas.**

- Android: long-press on row → bottom sheet with row actions. Standard Material long-press pattern adapted to Tether's visual language.
- iOS: long-press on row → context menu (iOS 13+ system context menu style). Standard iOS interaction.
- macOS: right-click on row → context menu popover. Standard macOS interaction.
- Desktop (JVM): right-click or application key → context menu. Standard JVM desktop interaction.
- All platforms: tap/click behaviour on Cases 1–2 and Case 3 is identical.

**Accessibility.**

- Case 1 / Case 2 rows: semantic role "button" or list item with activation action. Label: "[Device name], [online / paired]" — e.g. "Artem's MacBook, paired". Screen reader announces tappability.
- Case 3 rows: semantic role "list item" (not button). Label: "[Device name], offline, paired. Tap for hint." Screen reader announces that the row is not available for sending.
- The peer-identity accent (left-edge strip) is a non-text affordance. It must not be the only signal of pairing state — "paired" must also be conveyed in the semantic label.
- Inline hint (Case 3 tap): announced by screen reader as an alert or live region update.
- Focus order (Desktop): top bar → list rows top to bottom → (if hint is open) hint text → rest of list. Hint text does not trap focus.

## Flows

### Flow 1 — Wi-Fi off when opening the app (mobile)

1. User opens Tether. Local device has no Wi-Fi.
2. DeviceListScreen renders immediately in NoLocalNetworkState (mobile wording).
3. User sees: disconnected `•—•` illustration, "Wi-Fi is off", rationale, and — on Android — the "Open Wi-Fi settings" button.
4. On Android: user taps the button → OS Wi-Fi settings open. On iOS/macOS/Desktop: user opens the system menu / shade themselves per the written instruction.
5. User enables Wi-Fi, returns to Tether (via back gesture or app switcher).
6. Within a few seconds, DeviceListScreen transitions to the searching state (animated `•—•`).
7. Peers appear in the list as discovery finds them.

**Failure in step 4:** If the Android deep-link fails silently (rare), the screen remains on NoLocalNetworkState. No error toast is shown — the state is self-describing.

### Flow 2 — Network lost mid-session (all platforms)

1. User has DeviceListScreen open in PopulatedList state. Network drops (Wi-Fi toggle, airplane mode, cable pull).
2. Within a few seconds, the screen transitions to NoLocalNetworkState. All rows are replaced.
3. User restores the network.
4. Within a few seconds, the screen transitions to the searching state, then PopulatedList repopulates.

**Failure:** If the transition to NoLocalNetworkState is delayed beyond ~10 seconds (network state detection lag), the list may show offline rows briefly. Acceptable per the spec's "within roughly five seconds" budget; no additional error state required for this race.

### Flow 3 — Desktop with no usable network

1. User opens Tether on a desktop with no Wi-Fi and no Ethernet (or a network incapable of carrying Tether traffic).
2. DeviceListScreen renders in NoLocalNetworkState (desktop wording): "You're not on a local network", written instruction, no button.
3. User connects to a network via OS controls (outside Tether).
4. Tether detects the change and transitions to searching state automatically.

### Flow 4 — Desktop on Ethernet only

1. User opens Tether on a desktop connected via Ethernet to a LAN that can carry Tether traffic.
2. DeviceListScreen renders in searching state — no mention of Wi-Fi. Peers appear normally.

### Flow 5 — Offline paired device row

1. Local device is on a usable network. A known paired device is offline (powered off, different network, Tether not running).
2. DeviceListScreen shows the offline paired row (Case 3) — dimmed, with peer-identity accent.
3. User taps the row. Inline hint appears: "Not on this network. Make sure Wi-Fi is on and Tether is running on it."
4. User taps elsewhere. Hint dismisses.
5. The paired device comes online (joins the same LAN, starts Tether). The row transitions in place from Case 3 to Case 2 within a few seconds. No user action required.

**Failure:** If the device comes online but the row does not transition within ~10 seconds, the user has no recovery affordance — pull-to-refresh is not provided; the transition is automatic.

### Flow 6 — Local device loses network; offline paired rows disappear

1. Local device is on a network. Offline paired rows (Case 3) are visible.
2. Local device loses its network.
3. NoLocalNetworkState replaces the entire screen, including offline rows. The user's own network is the first problem to fix.
4. When the local device regains its network, searching state appears, then rows (including offline paired rows) repopulate.

## Navigation

DeviceListScreen is the root screen of the app. NoLocalNetworkState is a named state within that screen — not a separate destination. There is no push navigation, modal, or back stack involved in the transitions between states; the screen re-renders in place.

The "Open Wi-Fi settings" action launches the OS Settings app as an external intent/URL — it does not push a new in-app screen. The user returns to DeviceListScreen via OS back navigation or app switcher; Tether does not perform any action on return (the network change is detected automatically).

## Conceptual components

1. **No-local-network full-screen state** — the full-screen empty state replacing the device list when the local device has no usable network. Two copy variants (mobile / desktop) sharing the same layout structure.
2. **Disconnected brand-mark illustration** — the `•—•` mark in its disconnected state per [`ui-brand-mark.md`](../../../../engineering/ui-brand-mark.md) (both dots filled, dashed line). Static, used exclusively in the no-local-network screen.
3. **Open-settings action** — a platform-conditional teal button that triggers an OS deep-link to network settings. Renders only when the OS provides a stable deep-link; absent otherwise.
4. **Written network instruction** — a secondary-tone instruction line that replaces the action button on platforms without a stable settings deep-link.
5. **Peer-identity accent strip** — a contained left-edge visual marker in the `peerIdentity` hue applied to paired device rows (online & paired, offline & paired) to signal trust / prior relationship. Not interactive; not a text element.
6. **Dimmed device row** — the offline-paired row variant: same structure as a standard row, rendered at reduced opacity with the peer-identity accent retained.
7. **Offline row inline hint** — an in-place expansion (below the row, non-modal) triggered by tapping a dimmed device row. Displays a plain-language hint about the absent peer. Dismisses on tap/click elsewhere.
8. **Device row (standard)** — the base reachable-peer row (online & unpaired and online & paired share this base). Device name + secondary detail, tappable, leads to file-send flow.
9. **Row state transition** — the animated in-place dimming/brightening when a row moves between online-paired and offline-paired states (200–300 ms ease-out).

## Implementer layout calls

Not blocking — pick one and apply consistently:

- **Peer-identity accent placement.** Left-edge strip; if row geometry (leading icon, platform list insets) makes it awkward, fall back to a small dot or badge adjacent to the device name. Same choice for Cases 2 and 3.
- **Inline hint shape.** Inline expansion below the row; where impractical, fall back to a non-auto-dismissing bottom sheet (mobile) or tooltip (desktop). Transient toasts are not acceptable — the hint must not auto-dismiss.
- **Manual refresh affordance.** Not provided. Recovery is automatic.
