# UI Style Guide

Practical reference for Tether's visual identity. Audience: contributors writing or reviewing UI code. For the *why* behind these choices see [`adr/adr-visual-identity.md`](adr/adr-visual-identity.md); for the product-side framing see [`docs/product/design.md`](../product/design.md).

## Theme architecture

Tether uses Compose Foundation + [Compose Unstyled](https://composeunstyled.com/) (`com.composables:core`). There is no Material 3 dependency.

A custom `TetherTheme` composable wraps the content tree and exposes four theme objects to the composition: `TetherColors`, `TetherTypography`, `TetherSpacing`, and `TetherShapes`. Each is provided via a `CompositionLocal` so any composable in the tree can read current tokens without threading parameters.

Theme selection follows the platform's current appearance (light or dark) as reported by the OS. The theme updates live when the user toggles dark mode at the system level — no app restart required.

**Material 3 is not the base.** Use Compose Foundation primitives directly, or unstyled component libraries (e.g. Compose Unstyled, `com.composables:core`). `MaterialTheme` is not a dependency — any attempt to access it at runtime is an error.

## Color tokens

Token values for both themes:

| Token | Light | Dark | Usage |
|---|---|---|---|
| `surface` | `#FBFAF7` | `#15171A` | Page/screen background |
| `surfaceRaised` | `#FFFFFF` | `#1E2125` | Cards, sheets, elevated containers |
| `border` | `#E8E5DE` | `#2A2E33` | 1dp outline separating surface tiers |
| `textPrimary` | `#1A1A1F` | `#ECECEE` | Body text, device names, primary labels |
| `textMuted` | `#6B6B73` | `#9A9DA3` | Secondary labels, captions, timestamps |
| `accent` | `#2F7D6B` | `#3FA08A` | Active state, progress fills, interactive accents |
| `error` | `#B4423A` | `#E26A60` | Error text, destructive action labels |
| `copper` | `#C77E47` | `#D89968` | Brand mark only — never as a UI accent |

### Color rules

- **`accent` is the only interactive color.** Buttons, progress fills, focus rings, checked states — all use `accent`. No other color takes an interactive meaning.
- **`copper` is brand-only.** Use it exclusively in: the `•—•` glyph (right dot), the app icon, and the splash screen line animation. Never as a button, label, badge, or state color in the product UI.
- **Never hardcode color literals** outside the color token definitions. Read all colors from the current `TetherColors` instance via its composition local.
- **Elevation is expressed by surface tiers and `border`.** Use `surfaceRaised` + a 1dp `border`-colored outline for cards/sheets instead of shadow effects. This avoids the iOS Skia blur-shadow performance cost.

### WCAG AA status

All text-on-surface pairings pass AA (4.5:1 normal text / 3:1 large text). `copper` on `surface` (light) is ~3.1:1 — marginal for graphical objects (WCAG 1.4.11 requires 3:1); this is acceptable because copper appears only as a filled dot, never as the sole indicator of an interactive state. See ADR follow-ups if copper must appear in a text context.

## Typography

### Scale

| Role | Weight | Size | Line height | Letter-spacing | Tabular figures |
|---|---|---|---|---|---|
| `titleLarge` | 600 | 20sp | 28sp | −0.02em | no |
| `titleMedium` | 600 | 16sp | 24sp | −0.02em | no |
| `bodyLarge` | 400 | 15sp | 22sp | 0 | no |
| `bodyMedium` | 400 | 13sp | 20sp | 0 | no |
| `labelSmall` | 400 | 11sp | 16sp | 0 | no |
| `numeric` | 600 | 13sp | 20sp | 0 | yes (`tnum`) |

Use `numeric` for all file sizes, ETA values, and progress percentages. The `tnum` OpenType feature prevents digits from shifting width as values change, keeping progress rows stable during updates.

Every text style sets `LineHeightStyle` alignment to Center and trim to None. This keeps vertical metrics consistent between iOS and Android renderers.

### Bundling Inter Variable

Place the variable font file (`Inter-Variable.ttf`) at:

```
composeApp/src/commonMain/composeResources/font/Inter-Variable.ttf
```

Load it via `FontFamily` + `Font(resource = ...)` in `commonMain`. A single variable font file covers both the 400 and 600 weight axes; no separate files are needed.

## Spacing scale

| Name | Value | Usage |
|---|---|---|
| `xs` | 4dp | Tight inline gaps, icon-to-label spacing |
| `sm` | 8dp | Default list row padding, between-element gaps |
| `md` | 12dp | Standard content padding |
| `lg` | 16dp | State screens (empty, error) where breathing room clarifies a single focal element |
| `xl` | 24dp | State screens with a single prominent element |
| `xxl` | 32dp | Top/bottom page margins only |

**Default to `sm`/`md`** for list row padding and between-element gaps. Use `lg`/`xl` for state screens where breathing room clarifies a single focal element. `xxl` for top/bottom page margins only.

Never hardcode `dp` literals in composables — always read values from the current `TetherSpacing` instance via its composition local.

## Shapes

| Name | Corner radius | Usage |
|---|---|---|
| `sm` | 6dp | Chips, small tags, inline badges |
| `md` | 10dp | Cards, list item surfaces, dialog containers |
| `lg` | 14dp | Bottom sheets, large modal surfaces |

No pill/fully-rounded shapes (`CircleShape` on anything larger than 8dp tall) unless it is a pure icon button with circular affordance.

## Iconography — Tabler Icons

Dependency coordinate (add to version catalog and `composeApp/build.gradle.kts`):

```
br.com.devsrsouza.compose.icons:tabler-icons:1.1.1
```

Import icons per-symbol from the `tabler-icons` namespace. This keeps binary size small via tree-shaking — no bulk wildcard imports.

Rules:
- One stroke width across the app. Do not override `strokeWidth` per-callsite.
- Every icon that conveys meaning must have a non-null content description.
- Decorative icons (purely illustrative, accompanied by text that carries the meaning) use a null content description.
- Do not mix Tabler Icons with platform-native system glyphs. The content description fills the platform-native accessibility role.

## Brand mark — `•—•`

Full spec: [ui-brand-mark.md](ui-brand-mark.md).

The mark is the only place `copper` (`#C77E47` / `#D89968`) is allowed.
Everywhere else, accent is teal.

## Motion

| Use | Duration | Easing |
|---|---|---|
| State-change affirmation (peer appeared, transfer done) | 200–300ms | ease-out |
| Enter/exit transitions | 200ms | ease-out |
| `•—•` searching dot opacity pulse | 2000ms loop | linear |
| Transfer progress line fill | driven by real data, no fixed duration | spring (default damping) |

Rules:
- No animation for decoration. If removing it does not cost user clarity, remove it.
- No shadow effects with blur. Use a 1dp border drawn in `colors.border` for surface separation.
- Height changes on expanding rows (e.g. detail expansion) may use animated size transitions.

## Dark mode

The theme follows the platform's current appearance (light or dark) and updates live when the user toggles dark mode at the OS level — no app restart required.

A user override (System / Light / Dark) is planned — see `adr-visual-identity.md` Open follow-ups.

## Accessibility checklist

For each new screen or component:

- [ ] Every icon with semantic meaning has a non-null content description.
- [ ] Every interactive element has a human-readable semantic label if the visual label is not text.
- [ ] Color is never the sole signal of a state — always pair color with an icon or text label.
- [ ] Touch targets are ≥ 48dp on Android/iOS. Use `Modifier.minimumInteractiveComponentSize()` (Compose Foundation) if a visual element is smaller.
- [ ] On Desktop, interactive elements are reachable via Tab / Enter / Space. Verify focus order is logical (top-to-bottom, left-to-right in LTR layouts).
- [ ] `numeric` text style is used for file sizes, ETAs, and percentages — not `bodyMedium`.
- [ ] Text scales with system font size. No font size set outside the `TetherTypography` scale.

## Previews

Every screen composable requires a `@Preview` (or platform equivalent) for each state listed in the UX brief. Minimum set: populated, empty/searching, loading, error. Light + dark variants where they differ.

Previews must be self-contained: build fake state inline and pass it to a stateless variant of the screen. Do not instantiate Decompose components in previews. Previews live in `commonMain` unless the preview itself is platform-bound.

## References

- [ui-brand-mark.md](ui-brand-mark.md) — `•—•` geometry, states, and design rationale
- [adr/adr-visual-identity.md](adr/adr-visual-identity.md) — rationale and options considered
- [docs/product/design.md](../product/design.md) — product-side visual language
- [presentation-layer.md](presentation-layer.md) — Decompose component conventions
- [Inter typeface](https://rsms.me/inter/)
- [Tabler Icons](https://tabler.io/icons)
- [Compose Unstyled](https://composeunstyled.com/)
