# Design

How Tether looks and feels. This doc captures principles and key flows; per-screen specs live in feature docs. Implementation details live in [`docs/engineering/ui-style-guide.md`](../engineering/ui-style-guide.md); the full rationale behind the choices below is in [`docs/engineering/adr/adr-visual-identity.md`](../engineering/adr/adr-visual-identity.md).

## Principles

- **One visual language across all platforms.** Tether does not adopt the native look of each OS. The same layout, typography, palette, and iconography on Android, iOS, macOS, Windows, and Linux. Consistency is part of the value proposition — the user recognizes Tether instantly on any device.
- **Minimalism, not blank.** Few elements per screen, generous spacing, no decorative chrome. But each state (empty, searching, found, transferring, error) has a clear visual identity.
- **Two taps to send.** Pick device → pick file. Anything that adds a step needs to justify itself.
- **Show what's happening.** Discovery, pairing, transfer — every async action has a visible state. No silent waits.
- **Honest empty states.** When no peers are found, say *why* (e.g. "No devices on this Wi-Fi yet" / "Make sure both devices are on the same network").

Background reading: [Refactoring UI](https://www.refactoringui.com/) (Adam Wathan & Steve Schoger) — most of these principles appear there as concrete visual craft patterns with side-by-side examples. The closest single reference for the calm-utility / geometric-flat direction Tether takes.

## Visual Language

| Aspect | Choice |
|--------|--------|
| Theme stack | Custom `TetherTheme` (Compose Foundation + Compose Unstyled); no Material 3; see [style guide](../engineering/ui-style-guide.md) |
| Typeface | Inter Variable (bundled), weights 400 and 600; tabular figures for numbers |
| Palette | Warm off-white / near-dark-earth surfaces; teal as the sole interactive accent; `peerIdentity` (warm copper/amber hue) for peer-identity contexts |
| Iconography | Tabler Icons; one stroke weight; no platform-native glyphs |
| Spacing | Six-step scale (4 / 8 / 12 / 16 / 24 / 32 dp); `sm`/`md` for lists, `lg`/`xl` for state screens; see [style guide](../engineering/ui-style-guide.md) |
| Dark mode | First-class; tokens switch live at OS level; no app restart |
| Motion | State-change confirmations only; 200–300 ms ease-out; no decorative animation |
| Shapes | Sharp: `sm=6dp`, `md=10dp`, `lg=14dp`; no pill/fully-rounded surfaces |

Palette is warm off-white in light theme, near-dark-earth in dark theme, with a single teal accent for active state and progress. `peerIdentity` (warm copper/amber hue) identifies peer devices — peer-device rows and similar identity-display surfaces. Never as a UI interactive accent. Full hex values and WCAG ratios: [docs/engineering/ui-style-guide.md](../engineering/ui-style-guide.md).

Tether borrows Obsidian's discipline — single visual language across platforms, restraint, dark-first — but not its palette or information density. See [ADR](../engineering/adr/adr-visual-identity.md) for the full reasoning.

## Brand mark

A memorable brand mark is part of the identity. The slot is open and being redesigned — see #287. Until the redesign lands, the in-app brand-mark surfaces (searching indicator, transfer progress, success / error / disconnected affirmations) use the provisional `BrandMark` composable as a placeholder.

## Key Screens

### Device list (primary screen)

The screen the user opens Tether to see.

States:
- **Empty / searching** — animated brand-mark indicator + hint about Wi-Fi requirement.
- **Peers found** — list of devices with name, platform icon, and pairing status (paired vs. unknown).
- **No network / Wi-Fi off** — clear instruction to turn it on; no list shown.

Key interactions: tap a peer to send → file picker opens. Long-press / right-click for peer details (rename, unpair).

### Transfer / Progress

Visible on both sender and receiver during an active transfer.

States:
- **Sending** — file name, size, brand-mark progress indicator, ETA (tabular figures), cancel.
- **Receiving** — same, plus "Save to…" option (if applicable per platform).
- **Done** — success affirmation, "Open" / "Show in folder" actions.
- **Failed** — reason in plain language, retry action where applicable.

## Tone of Voice

- Plain language. No jargon ("peers" → "devices", "discovery" → "searching", "handshake" → don't surface).
- Short. UI strings under ~6 words where possible.
- Same wording across platforms — no localized neologisms per OS.

## Accessibility (minimum bar)

- All controls reachable by keyboard on desktop; by screen reader on mobile.
- Color is never the only signal of state (icons + text always accompany).
- Contrast meets WCAG AA in both themes.

## Out of Scope (here)

- Pairing screen and onboarding — described in their feature docs once written.
- Settings — minimal in MVP; covered when the surface grows beyond device name.
