# UX brief — Settings

**Spec:** [#222](https://github.com/khmelevartem/tether/issues/222) — no standalone spec doc; this foundation surface is scoped by the issue.
**Status:** `ready`

## Information architecture

The settings surface is a single host screen — SettingsScreen — reached from the device list via a gear control in that screen's top bar. The host renders a vertical stack of feature-owned settings sections in a fixed, typed order. It introduces no section of its own; today exactly one section is present.

```
DeviceListScreen
└── [tap gear] push → SettingsScreen
                       └── SettingsSection — File Transfer (owned by file-transfer)
```

Screens introduced: SettingsScreen.
Screens touched: DeviceListScreen's top bar carries the gear that enters here — the top bar and its gear are owned by [device-list/ux-brief.md](../device-list/ux-brief.md).
Sections rendered: SettingsSection — File Transfer, owned by [file-transfer/ux-brief.md](../file-transfer/ux-brief.md#settingssection--file-transfer).

## Screens

### SettingsScreen

**Purpose.** Host the app's configuration sections in one reachable place.

**Entry points.** The gear control in DeviceListScreen's top bar. No other entry point.

**Layout.**

- Top bar: back affordance in the leading position; "Settings" as the title.
- Content area below the top bar: a vertically scrolling stack of feature-owned settings sections in fixed order. With one section present, the content is that section rendered directly; the host adds no section header, divider, or padding chrome of its own beyond the standard content inset.

**States.**

- **Populated** — the only state. The fixed section stack is rendered. With exactly one section today, that section fills the content area. No empty state exists: the host always has at least its one typed section.

There is no loading state (sections render synchronously from local state), no error state, and no offline state at the host level — section-internal states are owned by each section's brief.

**Interactions.**

- Tap / click / activate the back affordance: pops SettingsScreen, returning to DeviceListScreen.
- Hardware back (Android), predictive back (Android), swipe-back (iOS): same as the back affordance — pops the screen.
- Scroll the content area: standard vertical scroll when section content exceeds the viewport. With one short section, content does not scroll on typical viewports; the area remains scrollable so longer future stacks behave without layout change.
- Section-internal interactions (toggles, save-location picker) are owned by the section's brief.

**Copy.**

- "Settings"

**Per-platform deltas.**

- Android: top-bar back arrow in the leading position; title left-aligned next to it. Hardware and predictive back pop the screen.
- iOS: top-bar back chevron in the leading position following iOS HIG; title centered. Swipe-back gesture pops the screen.
- macOS: top-bar back button (◀) in the leading position; title centered.
- Desktop JVM: top-bar back button (◀) in the leading position; title centered. The back button is keyboard-focusable; Esc is not bound to back (no platform convention for it here).

**Accessibility.**

- Back affordance: semantic label "Back". Role "button". It is the first focusable element in reading and focus order, ahead of the section content.
- Focus order (Desktop): back affordance → section content top to bottom (interactive rows in the section in their visual order).
- Title "Settings" is a heading for screen-reader structure navigation.

## Flows

### Flow 1 — Open and leave Settings

1. User is on DeviceListScreen and taps the gear in the top bar.
2. SettingsScreen pushes onto the stack. The user sees the "Settings" title, a back affordance, and the File Transfer section.
3. User adjusts a setting (owned by the section's brief) or simply reviews it.
4. User activates the back affordance (or uses the platform back idiom). SettingsScreen pops; DeviceListScreen is shown unchanged beneath.

## Navigation

SettingsScreen is a push onto the navigation stack from DeviceListScreen, not a modal and not a root. Back pops it and returns to the device list. The device list is never replaced — it remains beneath SettingsScreen in the stack.

## Conceptual components

1. **Settings host top bar** — a screen top bar carrying a leading back affordance and the "Settings" title. Title alignment follows the platform idiom (left on Android, centered on iOS/macOS/Desktop). The same screen-chrome pattern recurs across pushed screens; this brief uses it for SettingsScreen.
2. **Settings section stack** — the vertical, fixed-order container of feature-owned settings sections. Owns no section content; renders the typed sequence and scrolls when content overflows.

## Open UX questions

- None. This foundation surface is fully resolved from the issue scope and platform idioms.
