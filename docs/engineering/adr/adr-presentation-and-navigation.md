# Presentation & Navigation Architecture (ADR)

**Status:** Accepted — 2026-05-07
**Issue:** #51
**Blocks:** #7, #8, #11
**Note (2026-05-25):** `macosArm64` Kotlin/Native target removed from the build — see [adr-macos-native-vs-jvm.md](adr-macos-native-vs-jvm.md) §Reversal. Decompose's macOS-native support row in the table below is informational; not exercised in this codebase.

This ADR fixes how presentation logic and navigation are structured in Tether across Android, iOS, Desktop JVM, and macOS native. It supersedes the "Decompose is deferred" note in [architecture-principles.md](../architecture-principles.md).

## Context

Tether is a Compose Multiplatform app. Today `commonMain/App.kt` holds the project-template button — there are zero product screens. Several UI tasks are queued in parallel:

- #7 — Android device list
- #8 — Android send + progress
- #11 — Android pairing UI
- iOS / Desktop / macOS counterparts of all of the above (currently *tbd*)

Each of those tasks would otherwise have to answer the same architectural question on its own: **what is the ViewModel-equivalent, and how does navigation work across all four targets?** If the answer crystallizes inside the first UI task, it forms around one screen on one platform, and every later screen inherits accidental constraints. We fix it here, before the first product screen exists, so it can be validated against device list / send-with-progress / pairing dialog / UIKit entry points *simultaneously*.

### Constraints

- **Four targets:** Android, iOS, Desktop JVM, macOS native.
- **Single UI codebase.** Compose-MP is the rendering layer everywhere; we will not maintain a parallel SwiftUI tree.
- **Android config-change survival** is required.
- **iOS UIKit integration** is plausible — share sheet, file pickers, document camera. The presentation layer must let a UIKit entry point drive the same state holder a Compose screen drives.
- **Existing DI shape.** Composition happens in `AppContainer` (see [dependency-injection.md](../dependency-injection.md)). Components receive collaborators via constructor; presentation must keep that style.

## Decision drivers

| Criterion | Why it matters |
|---|---|
| KMP coverage | All four targets must be first-class. |
| Back stack semantics | Multi-step flows (device list → send → progress) need a real stack with predictable back/up behaviour. |
| Android config-change survival | Required (rotation, dark/light theme, locale). |
| UIKit interop | Some iOS flows enter from native UI (share sheet) and must drive the same state holder. |
| Testability without Compose | Presentation logic should be testable as plain Kotlin in `commonTest`. |
| Fit with `AppContainer` constructor DI | Components must take dependencies via constructor, not look them up. |
| Maturity | This is a load-bearing choice; replacing it later is expensive. |

### Validation scenarios

Each option below is judged against the same five scenarios:

1. **Device list.** Subscribe to `Flow<List<Device>>` from discovery; lifecycle of that subscription is tied to the screen.
2. **Send + progress.** Long-running operation; state survives Android rotation.
3. **Pairing dialog.** Modal overlay on top of another screen; back press closes the dialog, not the screen behind it.
4. **Back from active transfer.** Intercept back press, show a confirmation dialog before discarding the transfer.
5. **iOS share sheet.** Native UIKit entry point hands a payload to the presentation layer.

## Considered options

### 1. Decompose (chosen)

[Decompose](https://github.com/arkivanov/Decompose) is a KMP library for component-based architecture. A Component is a plain Kotlin class with explicit lifecycle, state holder, and child navigation. UI (Compose, SwiftUI, or UIKit) is a thin renderer that subscribes to the Component's `Value<State>`.

- **Targets:** all four (`androidTarget`, `iosArm64`, `iosX64`, `iosSimulatorArm64`, `jvm`, `macosArm64`, `macosX64`). Decompose's core has no Compose dependency.
- **Back stack:** first-class via `ChildStack` / `StackNavigation`. Push/pop/replace/deep-link.
- **Config-change survival:** `StateKeeper` (saved state) + `InstanceKeeper` (retained objects, ViewModel-style). Hooked into `AppCompatActivity` on Android.
- **UIKit interop:** the Component is UI-agnostic. A UIKit entry point can call Component methods directly without going through Compose first.
- **Testability:** Components are constructed with fakes; tests assert on `Value<State>` snapshots. No Compose runtime needed.
- **DI fit:** native — Components take a `ComponentContext` plus their collaborators via constructor. The composition root creates the root Component fully wired.
- **Maturity:** v3.x stable, actively maintained by Arkadii Ivanov, used in production at multiple JetBrains-adjacent projects.

**Cost:** new primitives every contributor must learn (`ComponentContext`, `ChildStack`, `Value`, `StateKeeper`); a one-screen feature still needs a Component, a Composable, and a wire-up at the root.

**How it handles the scenarios:**

1. Device list — `coroutineScope()` extension binds the discovery `collect` to component lifetime; cancelled on destroy.
2. Send + progress — transfer state lives in an `AppContainer` repository, not in the Component. The send Component observes the repository; rotation rebuilds the Component, the transfer keeps running.
3. Pairing dialog — `ChildSlot` overlay; its own back handler closes the slot before the screen.
4. Back from transfer — Component reads "is anything in progress?" from the AppContainer repository and registers a `BackHandler` accordingly to show a confirmation.
5. iOS share sheet — `RootComponent.onSharedFile(uri)` is called from the UIViewController code path.

### 2. Voyager

[Voyager](https://github.com/adrielcafe/voyager) is a Compose-only navigation library: Screens are Composables with attached ScreenModels.

- **Targets:** all Compose-MP targets, but logic is Compose-coupled.
- **Back stack:** built-in `Navigator` API.
- **Config-change survival:** ScreenModels are retained; `SavedStateHandle` integration on Android.
- **UIKit interop:** limited to what Compose-MP gives you (a single root `UIViewController`). A UIKit-side entry point cannot drive a Voyager Screen without going through Compose first.
- **Testability:** ScreenModels are testable, but their lifecycle is owned by the hosting Composable, which couples test setup to Compose fixtures.
- **DI fit:** acceptable — `getScreenModel { ... }` accepts constructor args, but the canonical pattern leans on a service-locator, fighting `AppContainer`'s "no global lookups" rule.
- **Maturity:** stable, popular, smaller maintainer team and slower release cadence than Decompose.

**Why not chosen:** the UIKit blind spot is decisive. The native iOS pickers / share sheet path is plausible from day one (see `docs/product/features/file-transfer/spec.md` and `pairing/spec.md`). With Voyager, that path forces a parallel non-Voyager state holder for the native side — exactly the fork the ADR is meant to prevent.

### 3. Thin custom layer (StateFlow + rememberSaveable + manual nav)

A hand-rolled approach: `class FooViewModel(scope: CoroutineScope, ...)` exposing `StateFlow<FooState>`, screens picked by a top-level `sealed class Screen`, back stack as a `List<Screen>` in a saveable.

- **Targets:** all, trivially.
- **Back stack:** ours to build. Realistic for two screens; gets expensive once nested flows, dialogs, and deep links appear.
- **Config-change survival:** `rememberSaveable` covers `Bundle`-serializable state; long-running ViewModels need their own retention plumbing (custom `ViewModelStoreOwner` or accept loss).
- **UIKit interop:** trivial — `StateFlow` is platform-agnostic.
- **Testability:** excellent.
- **DI fit:** native — constructor injection is the only style.
- **Maturity:** the code we write is exactly as mature as we make it.

**Why not chosen:** the absent pieces — back stack semantics, state restoration, deep linking, lifecycle hooks — are precisely what a navigation library provides. Re-implementing them would dominate the next two sprints and lock in macOS-native trade-offs before we know what they should be.

### 4. Compose Navigation Multiplatform (AndroidX Navigation, KMP port)

The AndroidX Navigation Compose library, recently extended to non-Android KMP targets (2.8.x+).

- **Targets:** Android stable; iOS / Desktop in beta; macOS native unverified.
- **Back stack:** native AndroidX Nav semantics.
- **Config-change survival:** `ViewModelStoreOwner` integration on Android; less clear on iOS.
- **UIKit interop:** none — Compose-only.
- **DI fit:** ViewModel factories work, but the Android-leaning lifecycle assumptions (e.g., `SavedStateHandle` semantics) leak into common code.
- **Maturity on non-Android:** the non-Android KMP support is recent; macOS native is the platform with the least track record.

**Why not chosen:** same UIKit blind spot as Voyager, plus the non-Android maturity story is years behind Decompose. Worth re-evaluating once iOS / macOS support stabilises.

### 5. Premo

[Premo](https://github.com/dmdevgo/Premo) is a KMP library implementing the Presentation Model pattern (Martin Fowler). `PresentationModel` is the state holder + lifecycle owner; navigation is a separate `premo-navigation` module with `StackNavigator`, `SetNavigator`, `MasterDetailNavigator`, and `DialogNavigator`. The library is intentionally UI-agnostic — the official sample drives the same PMs from Compose-MP, SwiftUI, UIKit, and React.

- **Targets:** Android, iOS (X64 / Arm64 / SimulatorArm64), JVM, JS, wasmJs. **macOS native is not configured** in Premo's Kotlin Multiplatform setup — adopting it would mean forking or upstreaming the target.
- **Back stack:** built-in via `StackNavigator`; siblings cover tabs, dialogs, master-detail.
- **Config-change survival + process-death restoration:** persistence is a first-class feature — PMs serialise via `PmStateHandler` and restore after process recreation. Stronger out-of-the-box than Decompose's default.
- **UIKit interop:** PMs are pure Kotlin; native UIKit code can drive them directly. Same property as Decompose.
- **Testability:** dedicated `premo-test` module with `runPmTest`. No Compose runtime needed.
- **DI fit:** PMs accept `PmArgs` (serializable) and a parent reference via constructor. Compatible with `AppContainer`.
- **Maturity:** v1.0.0-alpha.15 (May 2024), no commits since May 2024, single maintainer, ~200 stars. README explicitly warns: *"the library is in the pre-release alpha version. Stable work and backward compatibility are not guaranteed."* No stable release in five years.

**Why not chosen:** two blocking issues. (1) **macOS native is not supported** by the library's KMP configuration — and macOS native is one of our four required targets. (2) **Pre-release alpha + ~2 years of no commits + single maintainer** make this a risky bet for a load-bearing layer. The good ideas in Premo — process-death persistence as a first-class concern, parent-intercepts-navigation messaging — are noted as inspiration when extending the Decompose-based layer.

## Decision

**Tether adopts [Decompose](https://github.com/arkivanov/Decompose) as the presentation framework across all four targets.**

The pattern:

```kotlin
// commonMain — pure presentation, no Compose
class DeviceListComponent(
    componentContext: ComponentContext,
    private val discovery: MdnsDiscovery,
    coroutineScope: CoroutineScope = componentContext.coroutineScope(),
) : ComponentContext by componentContext {

    private val _state = MutableValue(DeviceListState.empty())
    val state: Value<DeviceListState> = _state

    init {
        coroutineScope.launch { discovery.devices.collect { _state.update { ... } } }
    }

    fun onDeviceClicked(id: DeviceId) { /* ... */ }
}

// commonMain — Compose UI, thin
@Composable
fun DeviceList(component: DeviceListComponent) {
    val state by component.state.subscribeAsState()
    // render state, forward events to component
}

// platform entry point (jvmMain / androidMain / iosMain / macosMain)
val root = RootComponent(
    componentContext = defaultComponentContext(),
    appGraph = appGraph,
)
setContent { RootContent(root) }
```

Shape rules:

- **Components are plain Kotlin** — they take their dependencies and a `ComponentContext` via constructor. No globals, no service locators.
- **Naming follows Decompose** — classes are `XxxComponent`, not `XxxViewModel`. They are not Android `ViewModel`s; the difference matters for tests, lifecycle, and KMP.
- **CoroutineScope is a default constructor argument** wired to the library's `coroutineScope()` extension on `ComponentContext`. Lifecycle-bound by default; tests pass an injected `TestScope` instead.
- **`AppContainer` builds the root Component**; child Components are created by their parents (or by a `ChildStack` configuration).
- **Compose subscribes via `subscribeAsState`** and forwards events as method calls. No `LaunchedEffect`-driven business logic.
- **Start with a single root Component and `ChildSlot` for modal overlays** (pairing dialog, confirmations). Add **`ChildStack`** when the first explicit back-press flow lands (likely send + progress).
- **One Component per logical screen / dialog.** Sub-state inside a screen stays inside the Component; we don't split presentation into ceremonial layers.
- **Long-lived domain state stays out of Components.** Active transfers, peer state, and other state that must outlive a screen live in repositories owned by `AppContainer`. Components observe these repositories; they never own such state and do not use `InstanceKeeper` for it.

## Consequences

**Positive:**

- Presentation logic is testable as plain Kotlin in `commonTest` — no Compose runtime, no Robolectric.
- Back stack, lifecycle, and state restoration come from a maintained library instead of from us.
- iOS UIKit integration is on the table from day one; share-sheet / native pickers don't force a rewrite.
- DI shape (`AppContainer` + constructor injection) generalises naturally — Components are constructor-injected like everything else.
- Per-screen scope question from [dependency-injection.md](../dependency-injection.md) is answered: long-lived domain state stays in `AppContainer` repositories; Components own only screen-local view state (and only for as long as the screen lives). `AppContainer` stays singleton.

**Negative / cost:**

- Every contributor must learn Decompose's primitives (`ComponentContext`, `ChildStack`, `Value`, `StateKeeper`).
- One-screen features still carry the Component / Composable / wire-up split — small ceremony tax.
- `ChildStack` configurations must be `@Serializable` (kotlinx.serialization) — a tiny constraint on what state goes into route arguments.
- **Process-death state restoration is shallower than Premo's first-class story** — full restoration takes more wiring on top of `StateKeeper`. Acceptable for Tether: flows are short-lived enough that a killed process means a fresh start. Revisit if persistence requirements emerge.
- Locks us into a third-party library at a load-bearing layer. Mitigated by Decompose's maturity and the fact that Components are isolated from UI — swapping the navigation framework later would not require rewriting screens.

**Effects on existing decisions:**

- [architecture-principles.md](../architecture-principles.md) is updated: the "Decompose is deferred" line becomes a pointer to this ADR.
- The composition-root sketch in [dependency-injection.md](../dependency-injection.md) extends to `RootComponent` construction; nothing in the existing rules changes.
- `App.kt` (the template button) will be replaced by the skeleton task that follows this ADR. That task adds the `decompose` dependency to `composeApp/build.gradle.kts`. **Out of scope here.**

## Per-platform feasibility

| Platform | Status | Notes |
|---|---|---|
| Android | ✅ Supported | `AppCompatActivity` integration via `defaultComponentContext()`; `StateKeeper` + `InstanceKeeper` covered. |
| iOS | ✅ Supported | `MainViewController` builds the root Component; UIKit entry points call Component methods directly. |
| Desktop JVM | ✅ Supported | `LifecycleRegistry()` driven by the Compose window. |
| macOS native | ✅ Supported | Decompose's core is pure Kotlin and supports `macosArm64` / `macosX64`. UI rendering is a separate concern (Compose-MP for macOS) and is out of scope for this ADR. |

## References

- [Decompose documentation](https://arkivanov.github.io/Decompose/)
- [architecture-principles.md](../architecture-principles.md)
- [dependency-injection.md](../dependency-injection.md)
- [modules.md](../modules.md)
