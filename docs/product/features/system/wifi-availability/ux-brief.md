# UX brief — Wi-Fi availability

**Spec:** [spec.md](spec.md)
**Status:** `ready`

## Information architecture

One screen — the device list. This brief specifies one thing on it: **NoLocalNetworkState**, a full-screen replacement for the device list when the local device has no usable network. Named state of the device list screen, not a separate destination. The list itself (rows, sorting, paired-offline visualisation) is owned by [device-list/ux-brief.md](../../device-list/ux-brief.md).

## Screens

### DeviceListScreen (existing — additional states only)

**Purpose.** The screen the user opens to find peers; this brief covers only the state where there is no usable local network — the cause is named and the path back is shown. Populated-state behaviour (rows, sort, hints) lives in [device-list/ux-brief.md](../../device-list/ux-brief.md).

**Entry points.** App launch (root screen). No other entry point.

---

#### State: NoLocalNetworkState (mobile — Wi-Fi off)

**Purpose.** Replaces the device list entirely when the local device has no usable network. Explains the cause in plain language and offers one direct action.

**Layout.**

- Full-screen centred layout with generous vertical spacing; no list, no search indicator.
- Illustration region: the brand mark in its **disconnected** state. Static, no animation. Visually distinct from the searching state.
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

- The brand-mark illustration region: semantic label "No network connection" (hidden from sighted users, announced by screen reader).
- "Open Wi-Fi settings" button: label matches visible text; announces as a button that opens an external app.
- Focus order (Desktop): illustration (skip, decorative) → title → rationale → button (if present).
- The screen must not trap focus; back / close remains reachable via keyboard on Desktop.

---

#### State: NoLocalNetworkState (desktop — no local network)

**Purpose.** Same full-screen replacement, but with neutral wording that does not assume Wi-Fi.

**Layout.** Identical structure to the mobile variant.

- Same disconnected brand-mark illustration.
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

## Flows

### Flow 1 — Wi-Fi off when opening the app (mobile)

1. User opens Tether. Local device has no Wi-Fi.
2. DeviceListScreen renders immediately in NoLocalNetworkState (mobile wording).
3. User sees: disconnected brand-mark illustration, "Wi-Fi is off", rationale, and — on Android — the "Open Wi-Fi settings" button.
4. On Android: user taps the button → OS Wi-Fi settings open. On iOS/macOS/Desktop: user opens the system menu / shade themselves per the written instruction.
5. User enables Wi-Fi, returns to Tether (via back gesture or app switcher).
6. Within a few seconds, DeviceListScreen transitions to the searching state (animated brand mark).
7. Peers appear in the list as discovery finds them.

**Failure in step 4:** If the Android deep-link fails silently (rare), the screen remains on NoLocalNetworkState. No error toast is shown — the state is self-describing.

### Flow 2 — Network lost mid-session (all platforms)

1. User has DeviceListScreen open with peers visible. Network drops (Wi-Fi toggle, airplane mode, cable pull).
2. Within a few seconds, the screen transitions to NoLocalNetworkState. All rows are replaced.
3. User restores the network.
4. Within a few seconds, the screen transitions to the searching state, then the list repopulates.

**Failure:** If the transition to NoLocalNetworkState is delayed beyond ~10 seconds (network state detection lag), the list may stay briefly. Acceptable per the spec's "within roughly five seconds" budget; no additional error state required for this race.

### Flow 3 — Desktop with no usable network

1. User opens Tether on a desktop with no Wi-Fi and no Ethernet (or a network incapable of carrying Tether traffic).
2. DeviceListScreen renders in NoLocalNetworkState (desktop wording): "You're not on a local network", written instruction, no button.
3. User connects to a network via OS controls (outside Tether).
4. Tether detects the change and transitions to searching state automatically.

### Flow 4 — Desktop on Ethernet only

1. User opens Tether on a desktop connected via Ethernet to a LAN that can carry Tether traffic.
2. DeviceListScreen renders in searching state — no mention of Wi-Fi. Peers appear normally.

## Navigation

DeviceListScreen is the root screen of the app. NoLocalNetworkState is a named state within that screen — not a separate destination. There is no push navigation, modal, or back stack involved in the transitions between states; the screen re-renders in place.

The "Open Wi-Fi settings" action launches the OS Settings app as an external intent/URL — it does not push a new in-app screen. The user returns to DeviceListScreen via OS back navigation or app switcher; Tether does not perform any action on return (the network change is detected automatically).

## Conceptual components

1. **No-local-network full-screen state** — the full-screen empty state replacing the device list when the local device has no usable network. Two copy variants (mobile / desktop) sharing the same layout structure.
2. **Disconnected brand-mark illustration** — the brand mark in its disconnected state. Static, used exclusively in the no-local-network screen.
3. **Open-settings action** — a platform-conditional teal button that triggers an OS deep-link to network settings. Renders only when the OS provides a stable deep-link; absent otherwise.
4. **Written network instruction** — a secondary-tone instruction line that replaces the action button on platforms without a stable settings deep-link.

## Implementer layout calls

- **Manual refresh affordance on no-local-network.** Not provided. Recovery is automatic — the screen transitions to searching as soon as the OS reports a usable network.
