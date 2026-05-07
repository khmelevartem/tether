# Presentation Layer

The presentation layer in Tether is built on [Decompose](https://github.com/arkivanov/Decompose). This document describes how it's structured and how to add to it. For the *why* — variants considered and trade-offs — see [adr/adr-presentation-and-navigation.md](adr/adr-presentation-and-navigation.md).

> **Status.** The skeleton (root Component + first screen + `decompose` dependency) lands in the task that follows the ADR. The conventions below are what the skeleton installs and what every screen written after it should follow.

## How it works

A **Component** is a plain Kotlin class that holds the state and lifecycle of one screen (or one logical flow). Compose subscribes to the Component's state and forwards events as method calls. A Component never imports Compose; UI never owns business state.

```
+-------------------+      +-----------------+      +----------------+
|  Composable       |      |  Component      |      |  AppGraph      |
|  DeviceList(...)  +------> state: Value    +------> repositories,  |
|  events as calls  |      |  fun onClick()  |      |  discovery,    |
+-------------------+      +-----------------+      |  network       |
                                                    +----------------+
```

Compose talks down to the Component. The Component talks down to `AppGraph` collaborators received via constructor — never the other way around.

## Component anatomy

```kotlin
class DeviceListComponent(
    componentContext: ComponentContext,
    private val discovery: MdnsDiscovery,
    coroutineScope: CoroutineScope = componentContext.coroutineScope(),
) : ComponentContext by componentContext {

    private val _state = MutableValue(DeviceListState.empty())
    val state: Value<DeviceListState> = _state

    init {
        coroutineScope.launch {
            discovery.devices.collect { devices ->
                _state.update { it.copy(devices = devices) }
            }
        }
    }

    fun onDeviceClicked(id: DeviceId) { /* ... */ }
}
```

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

A Component's lifetime is bound to the screen (or flow) it represents. Anything that must outlive a screen — active file transfers, peer state, long-running connections — lives in repositories owned by `AppGraph`. Components observe these repositories via injected dependencies and never duplicate the state internally.

In particular: **we do not use `InstanceKeeper` to retain domain state across configuration changes.** The repository in `AppGraph` already outlives the Activity; the Component just rebuilds and re-subscribes on rotation.

## Navigation

Decompose provides two navigation primitives we use, introduced one at a time as flows require them:

- **`ChildSlot`** — modal overlays (dialogs, confirmations, anything that sits on top of a screen). Added when the first dialog lands — e.g. the pairing dialog (#11).
- **`ChildStack`** — back stack with push / pop / replace; configurations are `@Serializable`. Added when the first explicit back-press flow lands — likely send + progress on top of device list (#8).

Both are wired in a parent Component and observed in Compose via `Children { ... }` / `subscribeAsState`. The skeleton itself starts with a single root Component; primitives are added incrementally.

See [Decompose: Navigation overview](https://arkivanov.github.io/Decompose/navigation/overview/).

## Configuration change (Android)

Decompose hooks into `AppCompatActivity` via `defaultComponentContext(...)`. When the activity is recreated on rotation, the root Component is rebuilt, but `StateKeeper` restores serializable view state and the underlying `AppGraph` repositories are untouched. No work needed in individual Components beyond putting any session-local view state through `stateKeeper.consume(...)` / `register(...)`.

Process-death state restoration is **not currently a goal** — Tether's flows are short-lived enough that a killed process means a fresh start. Revisit if persistence requirements emerge.

## Testing

Components are testable as plain Kotlin — no Compose runtime, no Robolectric:

```kotlin
class DeviceListComponentTest {

    @Test fun emits_devices_from_discovery() = runTest {
        val discovery = FakeDiscovery()
        val context = DefaultComponentContext(LifecycleRegistry())
        val component = DeviceListComponent(
            componentContext = context,
            discovery = discovery,
            coroutineScope = backgroundScope,
        )

        discovery.emit(listOf(deviceA, deviceB))
        runCurrent()

        assertEquals(listOf(deviceA, deviceB), component.state.value.devices)
    }
}
```

Patterns:

- Construct with `DefaultComponentContext(LifecycleRegistry())` — the registry becomes the test's lifecycle handle. Drive lifecycle transitions with `lifecycle.resume()` / `destroy()` when the test depends on them.
- Inject the test's `backgroundScope` (or a `TestScope`) as `coroutineScope` so coroutines run under the test dispatcher.
- Use **fakes**, not mocks of `HttpClient` / `MdnsDiscovery` (see [dependency-injection.md](dependency-injection.md)).
- Assert against `component.state.value` snapshots, or capture emissions via `Value.subscribe { ... }` for sequences.
- Common-test placement: presentation tests live in `commonTest` because the Component itself is `commonMain`.

## References

- [Decompose documentation](https://arkivanov.github.io/Decompose/)
- [Decompose: Component overview](https://arkivanov.github.io/Decompose/component/overview/)
- [Decompose: Navigation overview](https://arkivanov.github.io/Decompose/navigation/overview/)
- [Decompose: State preservation](https://arkivanov.github.io/Decompose/component/state-preservation/)
- ADR: [adr/adr-presentation-and-navigation.md](adr/adr-presentation-and-navigation.md)
