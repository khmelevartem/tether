# Presentation Layer

The presentation layer in Tether is built on [Decompose](https://github.com/arkivanov/Decompose). This document describes how it's structured and how to add to it. For the *why* — variants considered and trade-offs — see [adr/adr-presentation-and-navigation.md](adr/adr-presentation-and-navigation.md).

> **Status.** The layer covers `DeviceListScreen` (discovery + pending-files banner + drag-and-drop), `TransferProgressScreen`, `TransferSummaryScreen`, and their dialogs. Conventions below apply to every screen.

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

Compose subscribes via `subscribeAsState`:

```kotlin
@Composable
fun DeviceList(component: DeviceListComponent) {
    val state by component.state.subscribeAsState()
    Button(onClick = { component.onDeviceClicked(state.devices.first().id) }) { /* ... */ }
}
```

Events are plain method calls on the Component. No `LaunchedEffect` business logic, no event channels through the Composable.

## Long-lived state lives outside Components

A Component's lifetime is bound to the screen (or flow) it represents. Anything that must outlive a screen — active file transfers, peer state, long-running connections — lives in repositories owned by `AppContainer`. Components observe these repositories via injected dependencies and never duplicate the state internally.

In particular: **we do not use `InstanceKeeper` to retain domain state across configuration changes.** The repository in `AppContainer` already outlives the Activity; the Component just rebuilds and re-subscribes on rotation.

## Navigation

Decompose provides two navigation primitives we use:

- **`ChildStack`** — back stack with push / pop / replace; configurations are `@Serializable`. Used for the main navigation stack: `DeviceListScreen → TransferScreen`.
- **`ChildSlot`** — reserved for dialog-like surfaces that need an independent component lifecycle (deep-linkable dialogs, dialogs whose state must survive parent recomposition independently, etc.). Lightweight in-screen dialogs — confirmations, choosers — render via `androidx.compose.ui.window.Dialog` driven by component state variants instead.

`ChildStack` is wired in `RootComponent` and observed in Compose via `Children { ... }`. `ChildSlot` is introduced when a dialog-like surface genuinely requires its own component lifecycle.

See [Decompose: Navigation overview](https://arkivanov.github.io/Decompose/navigation/overview/).

## Configuration change (Android)

The root Component is created via Decompose's `retainedComponent { ... }` extension on `ComponentActivity` — **not** `AppCompatActivity` (we don't take that dependency; nothing in the app needs Material-AppCompat themes). `retainedComponent` stores the Component in the Activity's `ViewModelStore`, so it survives rotation without being rebuilt. The underlying `AppContainer` repositories are untouched.

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val component = retainedComponent { componentContext ->
            DeviceListComponent(componentContext = componentContext, discovery = container.mdnsDiscovery)
        }
        setContent { App(component) }
    }
}
```

Session-local view state that is *not* in a repository (transient UI flags, scroll position) can go through `stateKeeper.consume(...)` / `register(...)`, but the default for domain state is: don't duplicate it inside the Component — observe the `AppContainer` repository.

Process-death state restoration is **not currently a goal** — Tether's flows are short-lived enough that a killed process means a fresh start. Revisit if persistence requirements emerge.

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
