# UI rendering layer — Compose Multiplatform on every platform, no native UI tree

**Status:** Accepted — 2026-05-29
**Issue:** [#173](https://github.com/khmelevartem/tether/issues/173)

## Context

Tether ships one product to Android, iOS, and Desktop JVM (macOS / Windows / Linux). Each platform offers a native UI stack a contributor would reach for by default — SwiftUI or UIKit on Apple, the Android View/XML toolkit or Jetpack Compose on Android, Swing / native windowing on Desktop. Picking those would mean one UI codebase per platform.

The cross-platform parity promise is part of the product, not an implementation detail — the [vision](../../product/vision.md) frames every platform as a first-class peer rather than a flagship with ports. The visual identity is locked as a single system across all targets ([adr-visual-identity.md](adr-visual-identity.md)), and the presentation layer is structured so that UI is a thin renderer over UI-agnostic Decompose components ([adr-presentation-and-navigation.md](adr-presentation-and-navigation.md)). Both of those decisions already assume a single rendering layer; this ADR records the *why* behind that assumption, which they take as a given constraint.

The current state — Compose Multiplatform in `commonMain` shipping to all targets — lives in the parent living doc [architecture-principles.md](../architecture-principles.md) §Common-first ("Compose Multiplatform in `commonMain` ships to Android, iOS, and Desktop") and [presentation-layer.md](../presentation-layer.md). This ADR carries the rationale; those docs carry what is currently true.

## Decision drivers

| Driver | Why it matters for Tether |
|---|---|
| Cross-platform parity | Parity is a product promise ([vision.md](../../product/vision.md)). A parallel native tree on any platform makes that one "the real app" and the rest ports — the exact outcome the product rejects. |
| Single visual language | The locked identity ([adr-visual-identity.md](adr-visual-identity.md)) renders pixel-consistent only when one rendering layer draws it everywhere; native toolkits diverge on components, motion, and theming. |
| UI effort | One UI codebase in `commonMain` instead of one per platform. For a solo/small project the multiplier on every screen is decisive. |
| Developer familiarity | The maintainer is fluent in Compose and not in SwiftUI / UIKit / the native Android View toolkit. Building the UI in the stack the developer actually knows reduces delivery risk and cost directly; a native path would mean learning two more UI frameworks before the first cross-platform screen ships. |
| Renderer-agnostic presentation | The presentation layer ([adr-presentation-and-navigation.md](adr-presentation-and-navigation.md)) is UI-agnostic by design — components do not depend on Compose. A native UI could in principle drive them, but that option's cost is what this ADR weighs and rejects. |

## Considered options

### Option 1 — Compose Multiplatform as the single rendering layer everywhere (chosen)

One UI codebase in `commonMain`. Compose draws on Android (Compose for Android), on iOS (Compose Multiplatform for iOS, Skia-backed), and on Desktop JVM (Compose Desktop, Skia-backed) — and macOS ships through the Desktop JVM target rather than a separate Apple-native path. Closes parity, single visual language, and effort in one move, and fits the stack the maintainer already knows. Costs: Compose on iOS is younger than on Android, and Tether owns the full theme stack with no native-component fallback.

### Option 2 — Native UI per platform: SwiftUI / UIKit on Apple, native Android View toolkit, Swing / native on Desktop

Each platform gets its own UI written against the platform-default toolkit, all driving the shared Decompose components underneath. Closes native-idiom polish — each platform looks exactly like its OS expects. Costs: multiplies the UI surface by the number of platforms, breaks the single-visual-language guarantee (each toolkit themes and animates differently), and demands frameworks the developer does not know — turning every screen into a learning project on two extra UI stacks.

### Option 3 — Compose on Android + Desktop, SwiftUI only on Apple

A middle path: share Compose where it is most mature, write SwiftUI only for the Apple targets. Closes Apple-native polish while keeping shared UI on the other two. Costs: still forks the UI into two trees, still requires SwiftUI fluency the developer lacks, and still breaks pixel-parity on the forked platform — paying most of Option 2's costs for a fraction of its benefit, since the parity promise is violated the moment any one platform diverges.

## Decision

**Tether uses Compose Multiplatform as the single UI rendering layer on every platform — Android, iOS, and Desktop JVM (macOS / Windows / Linux) — and maintains no parallel native UI tree.**

The driver set converges: parity is a product promise, the visual identity is one locked system, a single UI codebase is the largest single effort multiplier available, and it is the stack the maintainer is fluent in. The native-idiom polish that Options 2 and 3 buy does not outweigh forking the UI into per-platform trees written in frameworks the developer would have to learn first. The presentation layer stays UI-agnostic regardless ([adr-presentation-and-navigation.md](adr-presentation-and-navigation.md)), so the door to a native renderer on a specific platform is not nailed shut — it is simply not worth opening today.

Compose on macOS is delivered through the **Desktop JVM** target (mature Compose Desktop), not the Kotlin/Native macOS path — that Native path was reversed for unrelated reasons ([adr-macos-native-vs-jvm.md](adr-macos-native-vs-jvm.md) §Reversal). Compose on the Desktop JVM and on Android is mature; Compose on iOS is officially supported and younger. None of the three rendering paths in use is experimental.

## Costs accepted

1. **Compose on iOS is younger than on Android.** Officially supported, but the iOS rendering path carries more risk of edge-case bugs than Android's. The locked visual identity already steers around the known Apple-Skia cost (no large-blur `Modifier.shadow()` — see [adr-visual-identity.md](adr-visual-identity.md)).
2. **No native-component fallback by default.** Tether owns the full theme stack rather than borrowing platform-native components; a control that a native toolkit would provide for free is ours to build. The cost is bounded on iOS: Compose Multiplatform embeds a native `UIView` / `UIViewController` inline via `UIKitView` / `UIKitViewController` ([CMP UIKit interop](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-uikit-integration.html)), so when a genuinely native control is required it is dropped into the Compose tree as a single inlined view — not a forked UI. This cost is accepted jointly with the custom-theme decision in [adr-visual-identity.md](adr-visual-identity.md).
3. **UI does not match each platform's native idiom by default.** A Tether screen looks like Tether on every platform, not like a stock iOS or Android screen. This is the intended trade for a single visual language, not a regression.

## Revisit if

- **Compose on iOS materially breaks UX** in a way the test and smoke loop cannot mask, with no upstream fix in sight. A native UI on the Apple targets — driven by the existing UI-agnostic components — moves onto the table, scoped to Apple only.
- **A platform-specific interaction genuinely cannot be expressed in Compose** (not merely "would look more native in SwiftUI"). The remedy is a per-platform composable or — on iOS — a native `UIView` / `UIViewController` embedded inline via `UIKitView` / `UIKitViewController` that still drives the shared component, not a parallel UI tree.

## References

- [architecture-principles.md](../architecture-principles.md) — §Common-first; parent living doc for what is currently true.
- [presentation-layer.md](../presentation-layer.md) — how Compose subscribes to components as a thin renderer.
- [adr-presentation-and-navigation.md](adr-presentation-and-navigation.md) — takes "Compose-MP everywhere, no parallel SwiftUI tree" as a constraint; this ADR supplies the why behind it.
- [adr-visual-identity.md](adr-visual-identity.md) — the single locked visual system this rendering choice reinforces.
- [adr-macos-native-vs-jvm.md](adr-macos-native-vs-jvm.md) — macOS ships via Desktop JVM, not Kotlin/Native.
- [vision.md](../../product/vision.md) — the cross-platform parity promise.
- [CMP UIKit interop](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-uikit-integration.html) — embedding native `UIView` / `UIViewController` inline in the Compose tree on iOS.
