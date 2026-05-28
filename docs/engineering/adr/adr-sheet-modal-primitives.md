# Bottom-sheet and modal primitives — Compose Unstyled

**Status:** Accepted — 2026-05-28
**Issue:** [#295](https://github.com/khmelevartem/tether/issues/295)

## Context

Tether bans `material` and `material3` imports — the theme stack is custom on top of Compose Foundation, codified in [ui-style-guide.md](../ui-style-guide.md). Bottom-sheet and modal-dialog primitives are needed across multiple features, and without a deliberate choice each consumer would hand-roll the same overlay machinery: outside-tap dismissal, IME-aware insets, focus trapping, system back / Esc handling, drag-to-dismiss for sheets, animated enter/exit, and accessibility wiring. That handcraft is precisely what Material gives away for free and what removing Material left as an open question.

The choice lives at the engineering layer, not the UI layer: it scopes the primitive library every sheet / modal in Tether is built on across Android, Desktop JVM, and iOS. The parent living doc is [ui-style-guide.md](../ui-style-guide.md), which references this ADR for the *why* and gains a one-line pointer for future authors.

## Decision drivers

| Driver | Why it matters for Tether |
|---|---|
| KMP coverage | The primitive must work on Android, Desktop JVM, and iOS from a single `commonMain` call site. Anything that forces per-platform reimplementation defeats the purpose of CMP. |
| Theming neutrality | The library must accept caller-supplied tokens (colors, shapes, spacing) and not impose its own `MaterialTheme`-shaped abstraction. Fighting a library's theme is the failure mode the `material`/`material3` ban exists to prevent. |
| Accessibility and platform behaviour out of the box | System back / Esc, focus trap, IME-aware insets, drag-to-dismiss, ARIA-equivalent semantics — getting these right per-platform is the bulk of the cost a primitive library is supposed to absorb. |
| License | Permissive (Apache 2.0 / MIT / BSD-style). Anything else is disqualifying for a dependency at this layer. |
| Maintenance signal | Recent releases, responsive maintainer, low backlog — the library sits under every modal surface, so a stalled project is a strategic risk. |
| Cost of adoption | Footprint, learning curve, ceremony per call site. A lighter primitive is preferable as long as the previous drivers hold. |

## Considered options

### 1. Compose Unstyled (chosen)

A renderless-component library on top of Compose Foundation: bottom sheet, dialog, dropdown, scrollbar, slider, checkbox, switch, tooltip, and others. Components expose state-and-behaviour without any visual opinion — the caller supplies modifiers, colors, shapes, and content composables. There is no theme abstraction the library imposes; the caller's design system is the only theme.

Primitives are published for Android, JVM (Desktop), iOS (X64 / Arm64 / SimulatorArm64), wasmJs, and js. License MIT. Actively maintained — release cadence is weekly-to-monthly, the maintainer (Composable Horizons) ships fixes within days, the issue backlog is small at decision time. Material is explicitly not a dependency.

What it closes: every accessibility / inset / back-handling concern the consumer would otherwise hand-roll, on every platform Tether targets, behind a theming surface that is exactly the shape the design system needs.

What it costs: a third-party dependency at a load-bearing visual layer, and neither release line is a clean baseline match. Tether's Kotlin baseline aligns with Compose Unstyled's 2.x line; Tether's Compose Multiplatform baseline aligns with the 1.x line (the 2.x line tracks a Compose Multiplatform alpha). The version catalog holds the line-family choice; baseline compatibility is verified before the coordinate is pinned.

### 2. Ad-hoc Compose Foundation overlay

Hand-rolled primitives on `androidx.compose.ui.window.Popup` / `Dialog` with `Modifier.offset`, `AnimatedVisibility`, manual gesture handling for drag-to-dismiss, and per-platform shims for IME-aware insets and system-back behaviour.

What it closes: no new dependency; the primitives are fully under Tether's control and can be styled trivially under `TetherTheme`.

What it costs: every concern listed in the drivers becomes Tether's to implement, test, and maintain across three platforms. Drag-to-dismiss alone is non-trivial; IME-aware sheet behaviour on Android requires a Window-level inset listener; iOS sheet swipe-to-dismiss semantics differ from Android in motion profile. Each future modal carries the same surface-area tax.

**Rejected.** Both decision-priority boxes from the issue are open at once with this option — cross-platform reuse and theming neutrality — but the build/maintain cost is exactly what Compose Unstyled absorbs at zero theming-cost penalty. Ad-hoc Foundation remains the right answer only if no candidate clears the priority; Compose Unstyled does.

### 3. AndroidX Compose Material 3 `ModalBottomSheet` / `AlertDialog`

The Material 3 reference implementations, KMP-published via Compose Multiplatform.

**Rejected.** The `material`/`material3` ban in [ui-style-guide.md](../ui-style-guide.md) is canon; Material 3 sheet and dialog APIs are out of scope by that ban. The ban exists because Material's theme system leaks across cross-platform renderers, the component motion profiles differ subtly between Android and iOS, and `Modifier.shadow()` with elevation is the iOS Skia performance trap the visual-identity ADR explicitly avoids.

### 4. SheetsComposeDialogs / sheets-compose-dialogs (Maxr1998 / Maxkeppeler ecosystem)

Community sheet/dialog libraries targeting Compose. The Maxkeppeler family (`sheets-compose-dialogs`) is the most-cited.

**Rejected.** Android-only (or with limited multiplatform support that does not cover iOS); built on Material 3 (defeats the ban); and the project's release cadence has slowed. Forces a per-platform reimplementation on iOS — the failure mode the decision priority is meant to prevent.

## Decision

Tether adopts **Compose Unstyled** as the bottom-sheet and modal-dialog primitive library across Android, Desktop JVM, and iOS. Any new bottom sheet or modal dialog must compose on Compose Unstyled primitives, restyled under the app theme via caller-supplied modifiers and content composables. Compose Foundation remains the substrate; Material 3 remains out.

## Costs accepted

- A third-party dependency sits under every sheet and modal surface. The risk is mitigated by the library's MIT license, healthy release cadence, low issue backlog, and the renderless design — components are state machines plus behaviour; the visual surface is Tether's, so a swap-out would not require rewriting screens.
- The Kotlin and Compose Multiplatform baselines become a coupling point split across release lines: Tether's Kotlin baseline aligns with the 2.x line, Tether's Compose Multiplatform baseline aligns with the 1.x line. The version catalog holds the line choice and exact coordinate.
- Contributors learn one more set of primitives. Acceptable: the API surface is small (state, expand/collapse, dismiss callbacks) and conceptually thinner than Material's component model.

## Consequences

- [ui-style-guide.md](../ui-style-guide.md) carries a short reference pointing authors of sheet / modal code at this ADR.
- Dependency wiring (version-catalog coordinate, module build configuration) is the responsibility of the first consumer task; this ADR does not perform that wiring.
- Sheet and modal surfaces that do not compose on this primitive set are out of conformance.

## Revisit if

- **The project is abandoned.** Definition: no release in 12 months and no maintainer response on the issue tracker. Action: re-open this ADR, evaluate forking versus ad-hoc Foundation, reverse if the cost has shifted.
- **KMP support breaks on iOS.** Definition: a release drops the iOS targets, or a regression on iOS goes unfixed for more than one release cycle and blocks Tether. Action: pin the last good version while re-evaluating; reverse if the regression is not addressed.
- **A required primitive lands outside the library's scope.** Definition: a sheet / modal pattern Tether needs is not in Compose Unstyled and the maintainer declines to add it. Action: extend with an in-tree primitive built on Compose Foundation; reverse the ADR only if the gap is broad rather than a single missing component.
- **The Compose Multiplatform baseline can no longer be satisfied.** Definition: Tether moves to a Compose Multiplatform version no Compose Unstyled release builds against, or the library drops support for Tether's version. Action: re-pin or re-evaluate.

## References

- [ui-style-guide.md](../ui-style-guide.md) — parent living doc; `material`/`material3` ban.
- [adr-visual-identity.md](adr-visual-identity.md) — locked the custom-theme direction this primitive choice extends.
- [Compose Unstyled](https://composables.com/compose-unstyled) — project site and component documentation.
- [Compose Unstyled on GitHub](https://github.com/composablehorizons/compose-unstyled) — source, releases, issue tracker.
