# Screenshot testing — Roborazzi + ComposablePreviewScanner on the Android target via Robolectric

**Status:** Accepted — 2026-05-22
**Issue:** [#127](https://github.com/khmelevartem/tether/issues/127)

## Context

The `/implement` skill runs a `spec → ux-brief → Compose code + @Preview → reviewer wave` cycle for UI features. UX-conformance is currently checked only textually by `review-ux` (copywriting, states, a11y read off the source). The visual side — what the screen actually renders — has no agent-readable artefact, so visual drift between code and UX brief can only be caught by a human.

[#127](https://github.com/khmelevartem/tether/issues/127) closes the loop with two halves that are useless apart: a headless preview-to-PNG renderer (subject of this ADR) and a vision-capable `review-visual` agent that compares the PNGs against the brief. This ADR scopes the renderer only.

Tether's KMP source-set layout puts Compose UI in `commonMain` ([modules.md](../modules.md)). Active targets: `androidTarget`, `jvm("desktop")`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`. `@Preview` composables live in `commonMain` and import `androidx.compose.ui.tooling.preview.Preview` (the unified annotation, supported across common and desktop in Compose Multiplatform 1.10+). Previews call stateless `XxxContent(state, callbacks)` variants and consume fake state from a shared `PreviewFixtures`.

Constraints from the issue that bound the option space:

- **KMP-aware.** Must discover and render `@Preview` defined in `commonMain` source sets — not just platform-specific ones.
- **Headless.** No emulator, no simulator. The renderer runs under `./gradlew` on a developer machine or CI worker.
- **`composeResources` from `commonMain` must work.** Tether's strings, drawables, fonts live there.
- **Auto-discovery.** One generic test that finds every `@Preview` by reflection; no hand-written test per preview (the failure mode is "agent forgets to add the test, screenshot silently missing").
- **CI-friendly.** No machine-specific assumption beyond JDK + Android SDK.

Explicitly **out of scope** per the issue: iOS/macOS preview rendering (no viable headless tool exists for Apple targets), Compose Desktop screenshots (Roborazzi's Desktop support is experimental), baseline-diffing in CI (`verifyRoborazziDebug`), and authoring new previews — `ui-expert` writes those.

The parent living doc for this subsystem is [`testing.md`](../testing.md); the "Screenshot tests" section there covers usage and rules.

## Decision drivers

| Driver | Why it matters for Tether |
|---|---|
| Renders `@Preview` from `commonMain` source sets | Tether's UI is `commonMain`-first; a renderer that only sees Android-source previews would require authoring duplicates and contradict the architecture invariant. |
| Reads `composeResources` from `commonMain` | All strings and assets live there; a renderer that can't resolve them produces blank or broken PNGs that the vision reviewer would flag as drift. |
| Headless (no emulator / simulator) | The renderer runs inside the `/implement` reviewer wave; spinning an emulator per cycle is incompatible with the orchestrator's latency budget. |
| Auto-discovery of previews | One generic test must rendering every preview; per-preview boilerplate guarantees gaps. |
| Tooling maturity for the chosen target | A flaky renderer in the agent loop turns into intermittent `review-visual` failures with no recourse. |
| Cost of platform expansion later | Apple-side preview rendering is out of scope today, but the choice should not preclude adding a second platform renderer if a viable tool appears. |

## Considered options

### Option 1 — Roborazzi + ComposablePreviewScanner on the Android target via Robolectric

Roborazzi (current `1.63.0`, May 2024 line, actively maintained — releases through `1.63.0` in 2026 with AGP 9.0 compatibility, race-condition fixes, and KMP-plugin restructuring) renders Compose previews in JVM unit tests via Robolectric, producing PNG files under `composeApp/build/outputs/roborazzi/`. ComposablePreviewScanner (`0.9.0`, May 2026; uses ClassGraph for bytecode-level discovery) scans configured package trees for `@Preview` and yields the preview metadata that Roborazzi captures. Since ComposablePreviewScanner `0.8.0+` together with Compose Multiplatform `1.10.0-beta02+`, the scanner walks `commonMain` source-set classes alongside Android ones via a single root prefix (e.g. `.scanPackageTrees("com.tubetoast.tether")`), which ClassGraph's `acceptPackages` matches by prefix against the full JVM test classpath — covering classes from any source set compiled into it. Renders are driven by one generic parameterised test in `composeApp/src/androidUnitTest/`.

Closes: KMP-aware (common previews via 1.10 + scanner 0.8+), headless (Robolectric on JVM), `composeResources` from `commonMain` works (Android resource pipeline resolves them), auto-discovery is the library's purpose, CI is a plain `./gradlew` task.

Costs: tied to the Android target's class-loading model — even `commonMain` previews render *as Android-target classes*; previews that depend on Apple-specific `actual`s cannot be rendered (acceptable — those are out of scope per the issue). Robolectric × Compose × Roborazzi versions are coupled and have flaked historically; we pin all three together and bump deliberately.

### Option 2 — Paparazzi + ComposablePreviewScanner on Android

Paparazzi renders Compose without Robolectric, using LayoutLib directly. Faster than Roborazzi on individual snapshots and has a longer track record.

Closes: headless, auto-discovery (via the same scanner).

Costs: **does not work for Tether.** Open bug [cashapp/paparazzi#2175](https://github.com/cashapp/paparazzi/issues/2175) (opened December 2025, still open as of this ADR) reports that previews depending on Compose Multiplatform `composeResources` from `commonMain` fail at render time. Every Tether screen uses common strings or assets — this is not a marginal limitation. Paparazzi is also Android-only in its source-set assumptions and has no roadmap for the KMP intermediate `commonMain` parent — the issue thread shows no upstream momentum.

### Option 3 — JetBrains / Google first-party Compose Preview Screenshot Testing

Google's `com.android.compose.screenshot` plugin (`0.0.1-alpha15+`) renders `@Preview` host-side as part of AGP. First-party tooling reduces third-party dependency surface.

Closes: headless, host-side.

Costs: **explicitly does not support KMP.** Per the official documentation: "Both the IDE and the underlying plugin are engineered exclusively for Android projects. They don't support non-Android targets in KMP projects." Requires AGP `9.0+` and Kotlin `2.2.10+`. Status is alpha with APIs "subject to change substantially". Not a viable choice for a `commonMain`-first codebase today; revisit if it gains KMP support.

### Option 4 — Self-rolled Robolectric harness without ComposablePreviewScanner

Write the discovery loop manually: enumerate `@Preview` functions via Kotlin reflection in a hand-written test, then drive `captureRoboImage` against each.

Closes: same render capability as Option 1, no third-party scanner dependency.

Costs: re-implements ComposablePreviewScanner badly. The scanner's value is `ClassGraph`-based bytecode scanning that handles `@PreviewParameter`, multi-preview annotations, and source-set traversal correctly. Re-deriving this is real ongoing work for no benefit; the maintenance falls on us instead of an actively-released library.

## Decision

**Choose Roborazzi + ComposablePreviewScanner on the Android target via Robolectric (Option 1).** Roborazzi's recent releases (through `1.63.0`) have actively addressed KMP-plugin layout, AGP 9.0 compatibility, and isolation of Android-specific code from non-Android KMP consumers (`1.58.0`), confirming the tool's trajectory toward the configuration Tether needs. ComposablePreviewScanner `0.8.0+` paired with Compose Multiplatform `1.10.0-beta02+` is the supported path for discovering `commonMain` previews; this is the explicit upstream-documented combination, not a workaround.

The canonical visual artefact for the agent cycle is the **Android-rendered version of a `commonMain` preview**. Apple-side and Desktop-side rendering remain out of scope; if a Tether screen behaves visually identically across targets (which is the design intent — Compose Multiplatform with a shared theme), the Android render is a sufficient witness for `review-visual`. Real per-platform appearance is verified by `/smoke-test`.

## Costs accepted

1. **Renders are Android-flavoured.** Previews resolve via Android classloading, Android `composeResources`, and Robolectric's Android-runtime fakes. Visual deltas that exist only on iOS / macOS / Desktop (Apple system font, platform-specific spacing) are not caught by this renderer. Mitigation: `/smoke-test` covers real per-platform appearance; visual drift between the *brief and code* is the agent loop's target, not platform-rendering parity.
2. **Robolectric × Compose × Roborazzi version coupling.** These three have flaked together historically. Tether pins all three explicitly in `composeApp/build.gradle.kts` and bumps as a set, not piecemeal.
3. **Previews that reference Apple-only `actual` symbols cannot be rendered.** This is structural — the Android-target classpath has no Apple `actual`s. Mitigation: previews target stateless `XxxContent(state, callbacks)` composables (issue convention), which by construction take no platform-specific dependency.
5. **Preview fixtures and private `@Preview` composables ship in release artifacts.** ComposablePreviewScanner discovers previews via classpath reflection, which requires them to be present on the production classpath — moving them to a test-only source set makes them undiscoverable. R8 may or may not strip private previews depending on retention rules. If binary footprint becomes load-bearing, ProGuard keep-out rules will be needed.
4. **Build-output PNGs are unversioned.** Baseline-diffing is out of scope per #127; the artefact's role is to be read by `review-visual` against the brief in the same PR run, not compared against a historical baseline. If baseline-diffing is added later, it gets its own issue and ADR amendment.

## Revisit if

- **JetBrains or Google ships first-party KMP-aware screenshot testing** in Compose Multiplatform proper or the AGP plugin. Trigger: re-evaluate Option 3 vs the third-party stack; reduce dependency surface if parity is achieved.
- **Paparazzi closes [#2175](https://github.com/cashapp/paparazzi/issues/2175) and gains real `commonMain` source-set understanding.** Trigger: reconsider for render-speed wins, especially if CI cost becomes load-bearing.
- **Roborazzi's Desktop-target screenshot support exits experimental.** Trigger: extend the renderer to also capture Desktop, removing the "Android-flavoured" caveat on screens where Desktop appearance materially diverges.
- **A viable headless Apple-preview renderer appears** (today the candidates spin a simulator). Trigger: add an Apple-side renderer in parallel; the agent loop reads both PNGs per preview.
- **Robolectric × Compose version coupling breaks the agent loop repeatedly.** Trigger: file a switch back to evaluating Paparazzi or the first-party plugin under their then-current state.

## References

- [Roborazzi](https://github.com/takahirom/roborazzi) — releases through `1.63.0` (2026), KMP-plugin restructuring in `1.62.0`, AGP 9.0 compatibility in `1.56.0–1.57.0`, non-Android KMP isolation in `1.58.0`.
- [ComposablePreviewScanner](https://github.com/sergio-sastre/ComposablePreviewScanner) — `0.9.0` (May 2026); ClassGraph-based bytecode discovery; CMP `1.10.0-beta02+` enables common-source-set `@Preview` walking via `.scanPackageTrees(...)`.
- [Compose Preview Screenshot Testing (Android)](https://developer.android.com/studio/preview/compose-screenshot-testing) — first-party plugin, `0.0.1-alpha15+`, AGP `9.0+`; documented as Android-only, no KMP support.
- [cashapp/paparazzi#2175](https://github.com/cashapp/paparazzi/issues/2175) — `composeResources` from `commonMain` unresolved as of 2026-05.
- Parent living doc: [`testing.md`](../testing.md) — "Screenshot tests" section.
- Sibling skill/agent contracts touched by the implementation: [`.claude/skills/implement/SKILL.md`](../../../.claude/skills/implement/SKILL.md), [`.claude/skills/code-review/SKILL.md`](../../../.claude/skills/code-review/SKILL.md), [`.claude/agents/review-visual.md`](../../../.claude/agents/review-visual.md) (the latter created by #127).
