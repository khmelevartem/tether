# Design

How Tether looks and feels. This doc captures principles and key flows; per-screen specs live in feature docs. Implementation details live in [`docs/engineering/ui-style-guide.md`](../engineering/ui-style-guide.md); the full rationale behind the choices below is in [`docs/engineering/adr/adr-visual-identity.md`](../engineering/adr/adr-visual-identity.md).

## Principles

- **One visual language across all platforms.** Tether does not adopt the native look of each OS. The same layout, typography, palette, and iconography on Android, iOS, macOS, Windows, and Linux. Consistency is part of the value proposition — the user recognizes Tether instantly on any device.
- **Minimalism, not blank.** Few elements per screen, generous spacing, no decorative chrome. But each state (empty, searching, found, transferring, error) has a clear visual identity.
- **Two taps to send.** Pick device → pick file. Anything that adds a step needs to justify itself.
- **Show what's happening.** Discovery, pairing, transfer — every async action has a visible state. No silent waits.
- **Honest empty states.** When no peers are found, say *why* (e.g. "No devices on this Wi-Fi yet" / "Make sure both devices are on the same network").

## Visual Language

| Aspect | Choice |
|--------|--------|
| Theme stack | Custom `TetherTheme` (Compose Foundation + Compose Unstyled); no Material 3 |
| Typeface | Inter Variable (bundled), weights 400 and 600; tabular figures for numbers |
| Palette | Warm off-white / near-dark-earth surfaces; **teal** as the sole interactive accent |
| Brand accent | `#2F7D6B` (light) / `#3FA08A` (dark) |
| Brand-only copper | `#C77E47` (light) / `#D89968` (dark) — appears only in the `•—•` mark and app icon |
| Iconography | Tabler Icons (`br.com.devsrsouza.compose.icons:tabler-icons:1.1.1`); one stroke weight; no platform-native glyphs |
| Density | Obsidian-restraint: `sm`/`md` spacing on lists; `lg`/`xl` only for state screens |
| Dark mode | First-class; tokens switch via `TetherColors`; no platform workaround needed |
| Motion | State-change confirmations only; 200–300 ms ease-out; no decorative animation |
| Shapes | Sharp: `sm=6dp`, `md=10dp`, `lg=14dp`; no pill/fully-rounded surfaces |

### Color tokens

| Token | Light | Dark | WCAG AA (on surface) |
|---|---|---|---|
| `surface` | `#FBFAF7` | `#15171A` | — |
| `surfaceRaised` | `#FFFFFF` | `#1E2125` | — |
| `border` | `#E8E5DE` | `#2A2E33` | — |
| `textPrimary` | `#1A1A1F` | `#ECECEE` | ✅ |
| `textMuted` | `#6B6B73` | `#9A9DA3` | ✅ (~4.8:1 / ~6.0:1) |
| `accent` | `#2F7D6B` | `#3FA08A` | ✅ (~4.7:1 / ~5.7:1) |
| `error` | `#B4423A` | `#E26A60` | ✅ |
| `copper` *(brand mark only)* | `#C77E47` | `#D89968` | ⚠️ ~3.1:1 graphical only |

## Memorable Element — `•—•`

The brand mark is a two-dot glyph: two filled circles connected by a single horizontal line of equal stroke weight.

- **Left dot** — teal. Semantics: your device.
- **Right dot** — copper. Semantics: the peer.
- **Line** — `textPrimary` tone. Neutral connector; not the accent color.

Appearances:

| Context | Behaviour |
|---|---|
| App icon | Static glyph on brand surface |
| Splash | Line draws itself left → right (~400ms) |
| Empty / searching state | Right dot hollow + slow opacity pulse (0.4 ↔ 0.7, 2s); left dot solid teal |
| Transfer progress | Line fills with teal left → right as bytes move; right dot stays copper |

**Copper is brand-only.** It never appears as a button color, hover state, focus ring, or interactive accent anywhere in the UI. The sole interactive accent is teal. This rule preserves a clean "one active accent" signal throughout the product.

## Key Screens

### Device list (primary screen)

The screen the user opens Tether to see.

States:
- **Empty / searching** — `•—•` with hollow right dot and slow pulse + hint about Wi-Fi requirement.
- **Peers found** — list of devices with name, platform icon, and pairing status (paired vs. unknown).
- **No network / Wi-Fi off** — clear instruction to turn it on; no list shown.

Key interactions: tap a peer to send → file picker opens. Long-press / right-click for peer details (rename, unpair).

### Transfer / Progress

Visible on both sender and receiver during an active transfer.

States:
- **Sending** — file name, size, `•—•` progress line, ETA (tabular figures), cancel.
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
