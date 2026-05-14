# Visual Identity — Palette, Typography, Iconography, Brand Mark (ADR)

**Status:** Accepted — 2026-05-14
**Issue:** #145

## Context

Tether had no locked visual identity. `docs/product/design.md` listed two open questions ("ship a custom icon set or borrow one?" / "pick a brand color in MVP?") and a table of vague choices ("one sans-serif", "neutral surface + one accent"). Several UI tasks were queued, and without a fixed system each feature implementation would make independent micro-decisions that compound into an inconsistent product.

This ADR locks the full visual identity before the first product screen ships on any platform beyond Android. The decisions are made once; the implementation guide (`docs/engineering/ui-style-guide.md`) and the design doc update (`docs/product/design.md`) are the distribution artifacts.

### Constraints

- Compose Multiplatform across Android, iOS, macOS (native, `macosArm64`), Desktop JVM. All targets share the same rendering layer; no SwiftUI tree.
- No Material 3 as a base — Tether owns the full theme stack. Full control and consistent cross-platform rendering without Material's opinionated component library; implementation cost accepted.
- Platform performance: iOS and macOS via Skia/CMP. Large blur shadows (`Modifier.shadow()` with large elevation) have a known cost on the Apple Skia backend; surface hierarchy must be expressed via tonal steps and 1px borders instead.
- Content is sparse by design: ~5 devices on the list at a time. A high-density information layout (Obsidian's graph view level) would look empty and over-engineered for this data scale.

## Obsidian as a design reference

Obsidian's visual language is a **partial fit — principles yes, palette and density no.**

- **What Tether borrows from Obsidian:** restraint, sharp corners, 1px borders for elevation, typographic clarity without decoration, no rounded-pill buttons, dark palette that takes color seriously.
- **Where Obsidian's approach does not transfer:** Obsidian's surface palette is cool-grey/near-black with a purple accent — neutral but cold. Tether's brand story ("your device connected to theirs") calls for warmth. The warm off-white / near-dark-earth surfaces in the palette below express that without reverting to a cheery consumer aesthetic. Obsidian's high-density text layout would produce over-engineered or empty screens against Tether's sparse data.

The operative framing: **Obsidian's discipline applied to Tether's sparse, warm content.**

## Considered options

### 1. Obsidian direct-clone

Adopt Obsidian's exact palette (cool-grey surfaces `#1E1E1E`/`#262626`, purple accent `#7C6CC1`, Inter typography) with only minor product-specific overrides.

**Rejected.** The cool palette clashes with Tether's warm-earth semantics. Purple is not uniquely identifiable as Tether's. The density playbook produces wrong results on 5-item lists.

### 2. Material 3 default

Use Material 3's dynamic color system with a teal seed color. Rely on M3 components (`Card`, `ListItem`, `LinearProgressIndicator`, `Button`).

**Rejected.** Material 3 renders differently across CMP targets; on iOS/macOS the component behaviour and motion profiles diverge from Android in subtle ways that require per-platform overrides — defeating the "one visual language" principle. The M3 color system's generated palettes (tonal containers, surface variants) introduce ~20 color tokens with no direct mapping to our sparse 6-token palette. `Modifier.shadow()` on iOS Skia is the direct performance cost. A custom theme gives us full control at acceptable complexity for this codebase size.

### 3. Things 3 — airy calm

Things 3-style: generous whitespace (`lg`/`xl` padding on list rows), fully-rounded cards, warm system grey, no border lines, translucent backgrounds.

**Rejected.** The airy spaciousness works when UI chrome fills the void (sidebar, tag lists, toolbar). Tether's primary screen is a short list of peer devices; the same generous spacing produces large empty regions, not calm. Rounded-card borders reduce required information density on the progress/ETA row without adding clarity.

### 4. Obsidian-restraint + Tether-warm (accepted)

Obsidian's typographic and layout discipline (sharp corners, border lines, low motion, small spacing scale, no decorative chrome) combined with a warm palette (off-white surfaces, near-dark-earth in dark mode, deep teal accent). Tether's sparse content calls for the lower end of the spacing scale, not Obsidian's maximum density.

**Chosen.** See Decision below.

## Decision

### Palette

| Token | Light | Dark |
|---|---|---|
| `surface` | `#FBFAF7` (warm off-white) | `#15171A` (near-dark earth) |
| `surfaceRaised` | `#FFFFFF` | `#1E2125` |
| `border` | `#E8E5DE` | `#2A2E33` |
| `textPrimary` | `#1A1A1F` | `#ECECEE` |
| `textMuted` | `#6B6B73` | `#9A9DA3` |
| `accent` | `#2F7D6B` (deep teal) | `#3FA08A` (lifted teal) |
| `error` | `#B4423A` | `#E26A60` |
| `copper` | `#C77E47` (brand mark only) | `#D89968` (brand mark only) |

Copper is a brand-recall asset — it appears in the `•—•` mark, the app icon, and the splash. It is not a UI interactive accent. Tether has exactly one interactive accent color: teal.

### Typography

Inter Variable, bundled in `composeApp/src/commonMain/composeResources/font/`. Two weights in use: 400 (body) and 600 (titles, device names, button labels). Tabular figures (`tnum` feature) for numeric output: file sizes, ETA, percentages. Letter-spacing: `-0.02em` on titles, `0` on body. `LineHeightStyle(alignment = Center, trim = None)` for cross-platform metric consistency.

### Iconography

Tabler Icons, via `br.com.devsrsouza.compose.icons:tabler-icons:1.1.1` (Compose Multiplatform). One stroke width across the app. No platform-native glyph mixing.

### Spacing scale

`xs=4dp`, `sm=8dp`, `md=12dp`, `lg=16dp`, `xl=24dp`, `xxl=32dp`. Default to `sm`/`md` for list rows; `lg`/`xl` for state screens (empty, searching) where breathing room aids clarity. `xxl` for section-level separation only.

### Shapes

`sm=6dp`, `md=10dp`, `lg=14dp` corner radius. Sharp relative to Material 3 defaults. No pill/fully-rounded surfaces.

### Motion

Compose stdlib animations only: `animate*AsState`, `AnimatedVisibility`, `animateContentSize`. 200–300 ms, ease-out easing, for state-change confirmations only. Never decorative. "Searching" state: slow opacity breathing on the right dot of `•—•` (`0.4 ↔ 0.7` alpha, `2s` loop). No spinner. No `Modifier.shadow()` with large blur — use border lines and tonal surface steps for elevation.

### Dark mode

`isSystemInDarkTheme()` used directly in `TetherTheme` (the CMP 1.5/issue-3575 iOS bug is resolved since CMP 2023-08; no `expect/actual` wrapper needed). User-override preference (System / Light / Dark) is a product feature — see Open follow-ups.

### Theme stack

Compose Foundation + `com.composables:core` (Compose Unstyled). Custom `TetherTheme` via `CompositionLocalProvider` exposing `TetherColors`, `TetherTypography`, `TetherSpacing`, `TetherShapes`. No Material 3 dependency. Details in `docs/engineering/ui-style-guide.md`.

### Brand mark — Interpretation I: "Two-Tone Tether" (chosen)

Signature glyph: `•—•`

- Left dot: teal (`#2F7D6B` / `#3FA08A`). Semantics: "your device."
- Right dot: copper (`#C77E47` / `#D89968`). Semantics: "the peer."
- Line: `textPrimary` tone, stroke weight equal to dot radius × 1.2.
- The line is NOT the accent color; it is a neutral connector.

Appearances:
- **App icon:** static glyph on the brand surface.
- **Splash:** line draws itself L → R over ~400ms on first launch.
- **In-app empty/searching state:** right dot hollow + slow opacity pulse (0.4 ↔ 0.7, 2s). Left dot solid teal. Communicates "your device is broadcasting; waiting for a peer."
- **Transfer progress indicator:** the line fills with teal from left to right as bytes move. Right dot remains copper (peer identity). Left dot solid teal (you).

The copper dot appears **only** in the brand mark contexts listed above. It is never used as a UI interactive accent, hover state, focus ring, or button color. Tether preserves the single-active-accent rule from `docs/product/design.md`: **teal is the sole interactive accent in the UI**.

## Consequences

**Positive:**

- One locked system eliminates per-feature palette drift before it starts.
- Custom theme gives complete control over cross-platform rendering; M3's tonal container proliferation does not leak into simple screens.
- Sparse data (≤5 devices) looks intentional at `sm`/`md` density rather than embarrassingly empty.
- `•—•` is a self-explanatory product story: it does not need a diagram in onboarding.
- Inter + tabular figures means ETA and size numbers align in lists without custom layout workarounds.
- No `Modifier.shadow()` dependency removes the known iOS Skia performance concern from the outset.

**Negative / cost:**

- Owning the full theme stack requires implementing `TetherColors`, `TetherTypography`, `TetherSpacing`, `TetherShapes` — 200–300 lines of infrastructure before any feature code.
- Bundling Inter Variable adds ~300 KB to all targets. Acceptable for a file-transfer app; revisit if app size becomes a distribution concern.
- Tabler Icons are imported per-icon (tree-shaking via per-symbol import); contributors must remember to import from the `tabler-icons` namespace rather than drawing local SVGs.
- The copper accent creates a two-color mark that must be reproduced faithfully in every icon format (Android adaptive, iOS, macOS, etc.). Minor asset management overhead.

## Open follow-ups

- **WCAG AA — copper on light surface.** `#C77E47` on `#FBFAF7` yields approximately 3.1:1 contrast — below AA for body text. However, copper is never used as standalone text in the UI: it appears only as a filled dot in the `•—•` glyph (graphical object, not text) and in the app icon. WCAG 1.4.11 Non-Text Contrast requires 3:1 for graphical objects against adjacent color — 3.1:1 is marginal. If the brand mark is ever used in a context where the copper dot must convey meaning against `#FBFAF7` without other signals, consider lifting to `#B86E38` (~3.6:1). No change to the locked hex in this ADR; revisit if a failing context surfaces.
- **User-override dark mode preference** (System / Light / Dark in Settings) — product feature, not part of this ADR. When implemented, it gates the `isSystemInDarkTheme()` call in `TetherTheme`.
- **Inter licensing.** Inter is OFL-1.1. Bundling is permitted; attribute in `NOTICE` or `licenses/` when that directory is created.
- **Tabler Icons version pinning.** `br.com.devsrsouza.compose.icons:tabler-icons:1.1.1` is the pinned version in the version catalog. Verify the coordinate before the first screen ships; icon geometry can change across releases.

## Research provenance

This appendix records the references and key findings that underlie the decision above.

### Angle 1 — Utility-app visual references

References include Obsidian, Things 3, Signal Desktop, Linear, Raycast, 1Password 8, Tailscale, LocalSend, and Warpinator, mapping the 2026 utility-app color space. Obsidian is a structural reference (principles, not palette). The "unclaimed corner" in the 2026 utility-app color landscape is a deep teal accent on a warm off-white surface — the basis for the palette in this ADR.

**Key finding:** Teal `#2F7D6B` on warm off-white `#FBFAF7` is visually distinct from every surveyed competitor. Anti-patterns confirmed: Material 3 dynamic color, paper-plane/cloud icons, sonar/radar metaphors, and multi-accent palettes.

### Angle 2 — Brand recall and memorable element

References include signature-color recall patterns (Slack purple, Spotify green, Linear gradient), signature symbol marks (Linear L, Telegram plane), motion signatures (Stripe rainbow, Arc space switch), and shape language (Anthropic waves, Apple corners), cross-referenced with the rarely-opened-app constraint: the mark must survive months between launches.

Three concrete mark candidates evaluated: the line `•—•`, a rope-knot monogram, and a two-tone split. `•—•` is recommended as primary — it carries triple duty as app icon, in-app status indicator, and transfer progress bar. Anti-patterns confirmed: radar arcs, paper planes, gradient meshes, terminal-green/cyberpunk neon.

**Key finding:** `•—•` is the strongest candidate. Its meaning (two endpoints, one connection) is self-evident and does not require onboarding. The two-tone treatment (teal + copper) gives it recall without violating the single-active-accent rule.

### Angle 3 — Compose Multiplatform feasibility

Four technical constraints apply: typography (Inter Variable in `composeResources/font/` ships on all CMP targets without `expect/actual`), iconography (Tabler Icons — `br.com.devsrsouza.compose.icons:tabler-icons:1.1.1` — has a maintained Compose Multiplatform artifact; the same `br.com.devsrsouza.compose.icons` group ships several other MP icon sets: feather, simple-icons, octicons, font-awesome, eva-icons, line-awesome, linea, css-gg. Lucide and Phosphor do not currently have maintained Compose MP ports on Maven Central; a custom SVG→ImageVector port would be required for either), dark mode (`isSystemInDarkTheme()` works directly on CMP 1.5+ — the iOS bug from CMP issue #3575 is resolved), and Material 3 (its tonal container system leaks Android-ness on iOS; dropping it in favor of a custom `TetherTheme` on Compose Foundation + Compose Unstyled is feasible for this codebase size). `Modifier.shadow()` with large blur carries a known cost on the iOS Skia backend; surface hierarchy via tonal steps and 1px borders is the replacement.

**Key finding:** The full Obsidian-restraint approach is feasible on all four CMP targets with no cosmetic `expect/actual`. Custom `TetherTheme` is the correct base. Tabler Icons is the verified-available geometric stroke icon set for Compose Multiplatform; it shares Feather's geometric discipline with Lucide (both are Feather descendants) making the visual difference imperceptible for Tether's restraint aesthetic.

### Convergence

The chosen configuration combines: drop Material 3, Inter + Tabler Icons, teal `#2F7D6B` on warm off-white `#FBFAF7` / near-dark-earth `#15171A`, `•—•` as the memorable mark, Interpretation I (two-tone tether: teal + copper) as primary. Interpretation II (split-background icon) is preserved as a reversible fallback — see below.

## Fallback — Interpretation II: "Split Background" (preserved, rejected)

App icon: a squircle split diagonally — warm off-white half on one side, near-black half on the other. The `•—•` glyph crosses the seam, with the teal dot on the warm side and copper dot on the dark side.

UI stays single-accent (teal only). The two-tone is icon-level only.

**Why rejected:** The split is visible only in the app icon. Inside the running app, the two-tone signal disappears — only teal is active. The mark loses its recall during use, the moment it matters most (transfer in progress). Interpretation I lets the `•—•` glyph appear live on-screen with both colors semantically intact: left dot teal (you, active device), right dot copper (peer), line filling with progress. That in-context meaning is the stronger choice. Interpretation II is a reversible decision: if user testing shows the two-tone icon is confusing without in-app presence, Interpretation I's in-app usage can be stripped back to single-accent while the icon retains the split.

## References

- [docs/product/design.md](../../product/design.md) — visual language and locked palette
- [docs/engineering/ui-style-guide.md](../ui-style-guide.md) — implementation reference
- [adr-presentation-and-navigation.md](adr-presentation-and-navigation.md) — Decompose presentation layer
- [Inter typeface](https://rsms.me/inter/) — OFL-1.1
- [Tabler Icons](https://tabler.io/icons) — MIT license
- [Compose Unstyled / composables:core](https://composeunstyled.com/)
