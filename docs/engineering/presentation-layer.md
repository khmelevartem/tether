# Presentation Layer

The presentation layer in Tether is built on [Decompose](https://github.com/arkivanov/Decompose). This document describes how it's structured and how to add to it. For the *why* — variants considered and trade-offs — see [adr/adr-presentation-and-navigation.md](adr/adr-presentation-and-navigation.md).

## How it works

A **Component** is a plain Kotlin class that holds the state and lifecycle of one screen (or one logical flow). Compose subscribes to the Component's state and forwards events as method calls. A Component never imports Compose; UI never owns business state.

```
+-------------------+      +-----------------+      +----------------+
|  Composable       |      |  Component      |      |  AppContainer      |
|  DeviceList(...)  +------> state: Value    +------> repositories,  |
|  events as calls  |      |  fun onClick()  |      |  discovery,    |
+-------------------+      +-----------------+      |  network       |
                                                    +----------------+
```

Compose talks down to the Component. The Component talks down to `AppContainer` collaborators received via constructor — never the other way around.

## Component anatomy

```kotlin
class DeviceListComponent(
    componentContext: ComponentContext,
    private val discovery: DeviceDiscovery,
    coroutineScope: CoroutineScope = componentContext.coroutineScope(),
) : ComponentContext by componentContext {

    private val _state = MutableValue(DeviceListState.empty())
    val state: Value<DeviceListState> = _state

    init {
        coroutineScope.launch {
            discovery.discoveredDevices.collect { devices ->
                _state.update { DeviceListState(devices) }
            }
        }
    }

    fun onDeviceClicked(id: DeviceId) { /* ... */ }
}
```

Components depend on **interfaces from `commonMain`** (e.g. `DeviceDiscovery`), not on platform actuals (`MdnsDiscovery`). The actual class implements the interface; the Component never sees the platform type. This keeps presentation tests in `commonTest` and allows fakes without `expect`/`actual` plumbing.

Conventions:

- **Naming.** Classes are `XxxComponent`. We follow Decompose's convention rather than calling them `ViewModel` — they are not Android `ViewModel`s, and the difference matters for tests, lifecycle, and KMP.
- **`ComponentContext` first, delegated.** Every Component takes `ComponentContext` as its first parameter and `: ComponentContext by componentContext` exposes lifecycle, `StateKeeper`, `InstanceKeeper`, and `BackHandler` without ceremony.
- **`CoroutineScope` is a default constructor argument** wired to the library's `coroutineScope()` extension. Lifecycle-bound by default; tests pass an injected `TestScope` instead.
- **Dependencies via constructor.** Same rule as everywhere else (see [dependency-injection.md](dependency-injection.md)). No globals, no service locators.

See [Decompose: Component overview](https://arkivanov.github.io/Decompose/component/overview/) for the full primitive surface.

## State and events

State is exposed as `Value<T>` — a Decompose primitive analogous to `StateFlow<T>`. Mutations go through `MutableValue<T>`:

```kotlin
private val _state = MutableValue(MyState.initial())
val state: Value<MyState> = _state

_state.update { it.copy(...) }
```

When the new value depends on the current value, mutate through `update { current -> next }` (from `com.arkivanov.decompose.value.update`), not `value = current.copy(...)`. `update` is an atomic compare-and-swap loop; the local read + value-write pair has a torn-write window where a concurrent emitter overwrites with a stale snapshot. Plain `value = next` is only safe when `next` does not depend on the current state.

Compose subscribes via `subscribeAsState`:

```kotlin
@Composable
fun DeviceList(component: DeviceListComponent) {
    val state by component.state.subscribeAsState()
    Button(onClick = { component.onDeviceClicked(state.devices.first().id) }) { /* ... */ }
}
```

Events are plain method calls on the Component. No `LaunchedEffect` business logic, no event channels through the Composable.

For states that are conceptually "empty or set", use an explicit empty sentinel on the state type (e.g. `PendingFilesSummary.NONE`) and keep `Value<T>` non-null. `Value<T>` has a non-null bound (`T : Any`); modelling absence as `null` and falling back to `StateFlow<T?>` is not the project convention.

## Long-lived state lives outside Components

A Component's lifetime is bound to the screen (or flow) it represents. Anything that must outlive a screen — active file transfers, peer state, long-running connections — lives in repositories owned by `AppContainer`. Components observe these repositories via injected dependencies and never duplicate the state internally.

In particular: **we do not use `InstanceKeeper` to retain domain state across configuration changes.** The repository in `AppContainer` already outlives the Activity; the Component just rebuilds and re-subscribes on rotation.

## Screens and previews

A screen is two composables in the same file:

- **`XxxScreen(component, modifier)`** — thin wrapper that subscribes to the Component's `Value<State>` and forwards events as callbacks. The only call site of the real Component.
- **`XxxContent(state, callbacks, modifier)`** — stateless. Renders the UI given a plain state object. No Decompose, no DI, no coroutines.

Every `@Preview` targets `XxxContent` — never `XxxScreen`. Previews live in `commonMain` next to the screen (`androidx.compose.ui.tooling.preview.Preview`, the unified CMP annotation). Build fake state from `PreviewFixtures` and wrap content in `PreviewSurface { }`, both under `com.tubetoast.tether.ui.preview`. This split is what lets Roborazzi render previews headlessly under Robolectric (the Decompose lifecycle does not boot in that environment) and lets `review-visual` consume the resulting PNGs against the UX brief — see [testing.md §Screenshot tests](testing.md#screenshot-tests).

## Navigation

The presentation tree is rooted in a single `RootComponent` (a concrete class) that owns a Decompose `ChildStack`. Composables render it via a single entry point — `RootContent(component)` — which carries the app theme and the `Children { ... }` switch. There is no separate theme wrapper above it.

Decompose navigation primitives are introduced one at a time as flows require them:

- **`ChildStack`** — back stack with push / pop / replace. In use. The root stack starts at a single initial child (the device list); pushed children are introduced as flows require them.
- **`ChildSlot`** — modal overlays (dialogs, confirmations) that sit on top of a screen without changing the back stack semantics. Not wired yet; added when the first dialog lands. Wired in the owning parent Component, not at the root.

Inline state on a screen (active transfer surfaces, banners, expansions inside a card) is *not* navigation — it lives in the screen's Component as plain state. Push-navigation is reserved for surfaces the user reaches via an explicit forward action and leaves with back.

### Restore-safe stack

The root `ChildStack` is constructed with `serializer = null`. Decompose then resets the stack to its initial configuration on every process recreation. This is the project-wide stance: a killed process means a fresh start at the device list, never a phantom child holding a reference to transient state (e.g. a per-peer detail screen for a peer that has since left the network).

A screen that genuinely needs to survive process death must lift its state into an `AppContainer` repository and re-subscribe on rebuild — same rule as for long-lived domain state.

### Identity in Configurations

Anything used as a `ChildStack` Configuration field must be a stable identity, not a snapshot of runtime coordinates. For peer-keyed screens that means a dedicated `@Serializable` identity type in the presentation package — never the live discovery `Device` record, whose `id` composes network coordinates and changes when those change. The Component resolves the identity to a live collaborator at construction time (typically by looking it up in an `AppContainer` repository) and exposes only what the screen needs.

See [Decompose: Navigation overview](https://arkivanov.github.io/Decompose/navigation/overview/) and [Decompose: ChildStack](https://arkivanov.github.io/Decompose/navigation/stack/overview/).

## Lifecycle and retention

### Android — `retainedComponent`

On Android, the root Component is created via Decompose's `retainedComponent { ... }` extension on `ComponentActivity` (we don't take an `AppCompatActivity` dependency — nothing in the app needs Material-AppCompat themes). The Component is stored in the Activity's `ViewModelStore` and survives rotation without being rebuilt; the underlying `AppContainer` repositories are untouched.

### Desktop and iOS — no retention indirection

`retainedComponent` is an Android-only extension because retention is an Android-only concern. On Desktop and iOS the root Component is constructed once in the platform entry point against a `LifecycleRegistry` driven directly by that entry point (`main()` for Desktop, `MainViewController()` for iOS). Process lifetime equals lifecycle; there is nothing to retain across. Do not introduce a multiplatform indirection to "unify" this — it would solve no problem.

### State restoration

Process-death state restoration is **not a goal** for the navigation stack. The root `ChildStack` uses `serializer = null` (see the Navigation section). Session-local view state that lives inside a single Component can still go through `stateKeeper.consume(...)` / `register(...)`; the rule is that domain state belongs in `AppContainer` repositories, not in the Component or in the saved-state bundle.

## Testing

Components are testable as plain Kotlin — no Compose runtime, no Robolectric:

```kotlin
class DeviceListComponentTest {

    @Test fun emits_devices_from_discovery() = runTest {
        val flow = MutableStateFlow<List<Device>>(emptyList())
        val lifecycle = LifecycleRegistry().apply { resume() }
        val component = DeviceListComponent(
            componentContext = DefaultComponentContext(lifecycle),
            discovery = FakeDeviceDiscovery(flow),
            coroutineScope = backgroundScope,
        )

        flow.value = listOf(deviceA, deviceB)
        runCurrent()

        assertEquals(listOf(deviceA, deviceB), component.state.value.devices)
    }
}
```

Patterns:

- Construct with `DefaultComponentContext(LifecycleRegistry())` — the registry becomes the test's lifecycle handle. Drive lifecycle transitions with `lifecycle.resume()` / `destroy()` when the test depends on them.
- Inject the test's `backgroundScope` (or a `TestScope`) as `coroutineScope` so coroutines run under the test dispatcher.
- Use **fakes**, not mocks of `HttpClient` / `MdnsDiscovery` (see [dependency-injection.md](dependency-injection.md)). Fakes live in `commonTest/.../<area>/Fake<Interface>.kt` next to the interface they implement — discoverable for the next test that needs the same fake without inlining or DRY-violation.
- Assert against `component.state.value` snapshots, or capture emissions via `Value.subscribe { ... }` for sequences.
- Common-test placement: presentation tests live in `commonTest` because the Component itself is `commonMain`.

## References

- [Decompose documentation](https://arkivanov.github.io/Decompose/)
- [Decompose: Component overview](https://arkivanov.github.io/Decompose/component/overview/)
- [Decompose: Navigation overview](https://arkivanov.github.io/Decompose/navigation/overview/)
- [Decompose: State preservation](https://arkivanov.github.io/Decompose/component/state-preservation/)
- ADR: [adr/adr-presentation-and-navigation.md](adr/adr-presentation-and-navigation.md)
