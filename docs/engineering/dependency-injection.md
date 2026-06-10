# Dependency Injection

How Tether wires its components — today, and where this is headed. This is also the **"does my code fit?" checklist** for new code.

## Phase 1 (current): Manual DI via composition root

A container — `AppContainer` — is the single place where shared components are constructed. Each platform's entry point creates the right container leaf, hands components to whoever needs them, and owns their lifecycle. No reflection, no DSL, no annotations. Components themselves never call `new` on their collaborators.

### Container hierarchy

The container hierarchy mirrors the source set hierarchy. Every layer adds only what its source set can actually see:

```
AppContainer            (commonMain)
├── JvmAppContainer     (jvmMain)
│   ├── AndroidAppContainer  (androidMain)
│   └── DesktopAppContainer  (desktopMain — ships on Windows / Linux / macOS)
│       └── CliAppContainer  (desktopCli)
└── AppleAppContainer   (appleMain)
    └── IosAppContainer     (iosMain)
```

`AppContainer` (commonMain) is no-arg. Intermediate containers — `JvmAppContainer`, `AppleAppContainer` — take a typed `*AppConfig` subtype in their constructor; leaves (`AndroidAppContainer`, `DesktopAppContainer`, `IosAppContainer`) may pass a concrete config up to the intermediate. `AppConfig` is the input — the values the entry point chooses (port, downloads dir, Android `Application`). The hierarchy of `*AppConfig` interfaces tracks the container hierarchy: [`AppConfig`](../../composeApp/src/commonMain/kotlin/com/tubetoast/tether/di/AppConfig.kt), [`JvmAppConfig`](../../composeApp/src/jvmMain/kotlin/com/tubetoast/tether/di/JvmAppConfig.kt), [`AndroidAppConfig`](../../composeApp/src/androidMain/kotlin/com/tubetoast/tether/di/AndroidAppConfig.kt), etc.

Concrete `*AppConfig` implementations are **named classes in their own files** (per [architecture-principles.md](architecture-principles.md) — "Named classes over anonymous objects"): [`TetherAppConfig`](../../composeApp/src/androidMain/kotlin/com/tubetoast/tether/di/TetherAppConfig.kt) for Android, [`DefaultDesktopAppConfig`](../../composeApp/src/desktopMain/kotlin/com/tubetoast/tether/di/DesktopAppConfig.kt), [`DefaultIosAppConfig`](../../composeApp/src/iosMain/kotlin/com/tubetoast/tether/di/IosAppConfig.kt). Anonymous `object : AppConfig { ... }` literals invite drift between call sites.

### Entry points

Each platform builds its container in its entry point and passes components down:

- **Desktop** — two entry points, each building its own container leaf:
  - [`Main.kt`](../../composeApp/src/desktopCli/kotlin/com/tubetoast/tether/Main.kt) — CLI runner (`desktopCli` source set), builds `CliAppContainer`. Launched via `./gradlew :composeApp:runDesktopCli` or `installCli` + `tether` wrapper. Clikt is only on this compilation's classpath.
  - [`MainUi.kt`](../../composeApp/src/desktopMain/kotlin/com/tubetoast/tether/MainUi.kt) — Compose UI runner (`desktopMain` source set, default for `nativeDistributions` packaging), builds `DesktopAppContainer`. Launched via `./gradlew :composeApp:run`.
- **Android** ([`TetherApp`](../../composeApp/src/androidMain/kotlin/com/tubetoast/tether/TetherApp.kt)): builds `AndroidAppContainer` lazily in the `Application` subclass and exposes it via the [`AppContainerProvider`](../../composeApp/src/androidMain/kotlin/com/tubetoast/tether/di/AppContainerProvider.kt) interface. [`TetherForegroundService`](../../composeApp/src/androidMain/kotlin/com/tubetoast/tether/network/TetherForegroundService.kt) reads it via `(application as AppContainerProvider).container`.
- **iOS** ([`MainViewController`](../../composeApp/src/iosMain/kotlin/com/tubetoast/tether/MainViewController.kt)): builds `IosAppContainer` outside the `ComposeUIViewController { ... }` lambda so the composable does not act as a composition root (see rule 5 below).
- **macOS**: ships via Desktop JVM (see [`adr-macos-native-vs-jvm.md`](adr/adr-macos-native-vs-jvm.md)) — no separate entry point; uses the Desktop `MainUi.kt` runner.

### The Provider pattern (Android)

Android's framework-managed components — `Activity`, `Service`, `BroadcastReceiver` — must have empty constructors. They cannot receive `AppContainer` through the constructor like normal components. The pattern that fits is:

1. The `Application` subclass implements `AppContainerProvider` and owns the container.
2. Framework components access it via `(application as AppContainerProvider).container`.

This is **not** a service-locator: `AppContainerProvider` is an interface implemented by exactly one type (`TetherApp` in production, a test `Application` in tests), and access is scoped to Android-framework-managed classes. Pure Kotlin classes still receive their collaborators through the constructor.

### Migration to Phase 2 stays cheap

The container hierarchy and provider pattern map directly onto Metro's shape. When we migrate, `AppContainer` becomes a `@DependencyGraph`, the platform subclasses become Metro contributions, and the `*AppConfig` interfaces become `@Provides` methods on a config graph. Tests that override `open val`s become test graphs with `@Provides` overrides. No structural reshuffle.

## Phase 2 (later): Migrate to Metro

When manual DI becomes painful, we move to [Metro](https://github.com/ZacSweers/metro) — a Kotlin compiler-plugin DI framework by Zac Sweers (Anvil author). v1.0.0 stable, April 2026.

**Why Metro specifically:**

- KMP-first. Supports all our targets explicitly: `iosArm64`, `iosSimulatorArm64`, JVM, Android.
- Compile-time graph validation. Missing bindings fail the build, not the device.
- Compiler plugin (FIR/IR), not KSP — faster builds than `kotlin-inject`.
- Anvil-style `@ContributesBinding` / `@ContributesTo` fits the multi-module shape we're heading toward (see [modules.md](modules.md)).

**Why not Koin:** runtime resolution. In a KMP app where every target gets tested independently, "forgot to register a binding" turns into a runtime crash on the platform we tested last. We prefer compile-time errors.

**Why not kotlin-inject:** technically excellent, but KSP-based — slower build than a compiler plugin, and the multi-module ergonomics with `kotlin-inject-anvil` overlap with what Metro gives us natively.

**Why not Hilt / Dagger:** not KMP. Dagger generates JVM bytecode, Hilt is Android-locked.

## Migration trigger (when Phase 2 starts)

We move to Metro when **any one** of these hits:

- We split out a third Gradle module and wiring across module boundaries becomes tedious in the container.
- We forget to register something for the first time and ship a crash to a platform.
- A container leaf grows past ~30 lines or starts branching by build flavor.

Until then, a compiler plugin is more apparatus than the problem deserves.

## Rules for new code (the "does it fit?" checklist)

These apply now, in Phase 1, and survive into Phase 2 unchanged.

### 1. Constructor injection only — don't `new` your collaborators

❌
```kotlin
class SomeService {
    private val repo = SomeRepository()  // owns its collaborator — can't be replaced in tests
}
```

✅
```kotlin
class SomeService(private val repo: SomeRepository) { ... }
```

No `lateinit var repo: SomeRepository` set after construction. No service-locator lookups inside methods. Dependencies are explicit constructor arguments — a class's constructor signature is its honest dependency list. Anything reached through globals is invisible.

A nullable dependency with a `= null` default is the same dishonesty: a collaborator the production composition always supplies looks optional, and a caller that omits it silently disables the feature with no compile error. Make it required; update old tests to pass a fake. Reserve the nullable default for a genuinely optional dependency — and then test the null branch.

The composition root creates shared instances once and hands them to every component that needs them. Tests construct fakes.

### 2. Platform context is a dependency, not a global

❌
```kotlin
// platformMain
actual class SomeService {
    private val manager = GlobalApp.context.getSystemService(...)  // global lookup
}
```

✅
```kotlin
actual class SomeService(context: PlatformContext) {
    private val manager = context.getSystemService(...)
}
```

The composition root in the platform entry point already has the application context — pass it in.

### 3. One singleton, one owner

If a component is supposed to be a singleton, it is created exactly once in the composition root. Don't put it in a Kotlin `object`. Don't lazy-init it from inside another component.

The composition root is the only place that knows lifecycles. Everywhere else, you receive what's already alive.

The logging façade ([`KydraLog`](logging.md)) is the documented exception: a process-global cross-cutting infra singleton initialised once per platform entry point, accessed directly (`KydraLog.withTag(...)`) without passing through the graph. Rationale and trade-offs in [`adr/adr-logging-kydra.md`](adr/adr-logging-kydra.md).

**`AppContainer` composes; entry points start.** A singleton that owns a long-lived collector or background subscription is constructed lazily in `AppContainer` like any other graph node, but its `start()` is invoked from each platform's entry point (Android `Application.onCreate`, Desktop `main`, iOS `MainViewController`, future CLI `main`) alongside `fileServer.start()` / `mdnsDiscovery.start()`. The base container cannot do this from its own `init` block — abstract collaborators are not yet assigned when `AppContainer.<init>` runs. Adding the `start()` line to a new entry point is the explicit, compile-noisy way to opt in; a forgotten line surfaces as silent inactivity, not a crash.

**Domain-layer orchestrators live in the relevant domain package.** When a singleton coordinates domain primitives (e.g. `AutoSendDispatcher` reading `peersRepository.peers` + `pendingFilesRepository.pending` + `peerPreferencesStore` and driving `PeerTransferEngineRegistry`) it goes into the matching domain package (`com.tubetoast.tether.transfer`, etc.), not into `presentation`. CLI and UI then share the same orchestrator through `AppContainer` without each surface re-implementing the rule.

### 4. Don't instantiate dependencies inside composables

❌
```kotlin
@Composable
fun SomeScreen() = ComposeUIViewController {
    val repo = SomeRepository()
    val service = SomeService(repo)   // wrong — new instance on every recomposition
    DisposableEffect(Unit) {
        service.start()
        onDispose { service.stop() }
    }
}
```

This is a DI violation: the composable is acting as a composition root. It creates a dependency itself instead of receiving it.

Two problems:
1. `DisposableEffect(Unit)` runs once, capturing the instance from the first composition. On every recomposition, a new `SomeService(repo)` is created and immediately discarded — never started, never stopped. Memory leak.
2. Even with `remember { SomeService(repo) }` to fix the leak, the composable still owns the lifecycle of a singleton — it shouldn't.

✅ The correct shape: build the container in the platform entry point, pass components into the composable.

```kotlin
// platform entry point = composition root
fun entryPoint() = run {
    val container = Container(DefaultConfig())
    ComposeUIViewController {
        DisposableEffect(Unit) {
            container.service.start()
            onDispose { container.service.stop() }
        }
        App()
    }
}
```

The container is created **outside** the `ComposeUIViewController { ... }` lambda; the composable receives ready-made components, never calls a constructor.

### 5. Don't introduce an interface for one implementation

This rule contradicts the cargo-cult version of Clean Architecture. We follow it consciously (see [architecture-principles.md](architecture-principles.md)). An interface earns its place when:

- there is a fake/test implementation, OR
- there are two real implementations (e.g. JVM and iOS file servers).

A `SomeTransferRepository` interface with one `SomeTransferRepositoryImpl` adds noise, not safety.

### 6. Test seams use fakes, not wrapper interfaces

When you do need to test a class that depends on a collaborator, prefer a hand-rolled fake or a test-double provided by the library — don't introduce a wrapper interface just to mock it. If a fake is hard to write because the component is too coupled to its collaborators, fix the component.

### 7. The container exposes named components, not plain data

A container field is a named object that owns or manages something — a store, a server, a client, a scope, a factory. It is not a primitive, a string, a number, a collection, or any other plain data shape. Values like an identity, an alias, a port, a device-type tag belong to the component that owns them; consumers receive the owning component and ask it for the value at the moment they need it, including across an asynchronous boundary if the value is only known after I/O. Putting plain data on the container forces materialisation at construction time, hides who is responsible for the value, and turns the container into a property bag instead of a wiring graph.

### 8. No silently-throwing common defaults

When a container field needs a per-platform implementation, the common contract states that explicitly: the field is `abstract` on the common container so every platform leaf is forced to declare a value, or the leaves declare explicit stubs that throw with `TODO(#<tracking-issue>)` referencing the issue that implements the real value. A concrete common default that throws unconditionally and works only because one platform happens to override it is the worst of both shapes: it compiles, satisfies fake-based tests, and surfaces only at runtime on the platforms that forgot to override. The `abstract` form makes the gap a compile error; the explicit-stub form makes the gap a discoverable `TODO` with an owner.

### 9. Branch on the host OS once, behind a provider

Within a platform that bundles several operating systems (Desktop = Windows / Linux / macOS), resolve the host OS exactly once and deliver every OS-specific dependency as a delegate. A sealed provider with one object per OS, selected at a single internal point, is the only place a `when (os)` lives; the container, config, and tests consume `provider.someDelegate(...)` and never re-read `os.name`.

```kotlin
internal sealed interface DesktopPlatform {
    fun mdnsDelegate(...): DeviceDiscovery
    object MacOs : DesktopPlatform { /* Bonjour */ }
    object Windows : DesktopPlatform { /* JmDNS */ }
    object Linux : DesktopPlatform { /* JmDNS */ }
}
internal val desktopPlatform = desktopPlatformFrom(System.getProperty("os.name").orEmpty())
```

A public `currentOs` enum that any call site can branch on is the anti-pattern: OS forks then scatter and drift. Keep the OS axis orthogonal to other axes (e.g. GUI-vs-CLI stays in the container hierarchy) so the provider doesn't multiply into a class-per-combination. Inject the provider into the container with a default (`platform: DesktopPlatform = desktopPlatform`) so a test can pass `DesktopPlatform.Linux` on any host.

## Testability

The container is also the seam tests use to substitute fakes. Three levels of tests, three patterns:

### 1. Unit tests of components (no container needed)

Components take their dependencies through the constructor and are tested directly. The container exists for production wiring only — tests construct what they need.

### 2. Tests of consumers that depend on container components

When a class takes container components, the test builds a fake container by subclassing and overriding `open val`s — exactly what the `open val` declarations are for:

```kotlin
class FakeContainer : AppContainer() {
    override val namePersistence = <fixture>
    override val mdnsDiscovery = <fake>
    override val fileServer = <fake>
    override val trustedDeviceStore = <fake>
}
```

(For one-shot test fakes, an inline `object : Config` literal is acceptable — the named-class rule has its exception there. Reused fakes get extracted to a named class.)

### 3. Tests of Android framework components

`Service`/`Activity` tests under Robolectric need an `Application` that implements `AppContainerProvider`. The simplest path is `@Config(application = TetherApp::class)` — Robolectric instantiates the real `TetherApp`, the lazy container builds against the Robolectric-shadowed `getExternalFilesDir`, etc.

When a test needs a fake container instead of the real one, define a `TestApplication : Application(), AppContainerProvider` that exposes a `FakeAndroidAppContainer`, and switch `@Config(application = TestApplication::class)`. This stays out of any global static — each test method gets a fresh instance through Robolectric.

### Testability rules to keep this working

1. **Container fields are `open val`.** Override is the substitution mechanism. No setters, no `lateinit var`.
2. **`AppConfig` and its subtypes are interfaces.** Tests implement them with named classes (or, exceptionally, anonymous objects for one-shot test cases).
3. **Common-logic tests don't reach for a platform container.** They construct a minimal `AppContainer` subclass in `commonTest`.
4. **Don't mock components, fake them.** Mock frameworks force a layer of indirection that pollutes production code.

## Per-screen scoping

`AppContainer` is a singleton. There is no separate per-screen DI scope — per-screen state and lifecycle are owned by Decompose Components, not by an Android-style ViewModel container. Each Component receives the dependencies it needs from `AppContainer` (or from its parent Component) and constructs its children directly.

The platform entry point builds the **root Component** (Decompose), passing `AppContainer` to it. The root creates its children with the dependencies they need. On Android we use Decompose's `retainedComponent { ... }`, which stores the Component in the Activity's `ViewModelStore` for config-change retention only — not as a DI scope. See [presentation-layer.md](presentation-layer.md).

## See also

Per-layer ownership and import rules (which layer may accept which dependencies): [layering.md](layering.md).

## Open questions

- iOS receive-side: when a non-Ktor `FileServer` implementation lands, the container branches per-platform more aggressively. May be the moment to flip to Metro.
- Public/Internal container split (per the library DI pattern): not needed in a single-module app; revisit when the first lib module is extracted.
- `desktopCli` testable symbols (`parseTokens`, `handleSend`, `formatBytes` in `Main.kt`) are `public` instead of `internal` — `desktopTest` is a separate compilation from `desktopCli`, and KMP does not auto-create a test compilation for custom compilations. Wiring a dedicated `desktopCliTest` compilation (with its own JUnit task) would restore `internal`. Acceptable while CLI surface is one file; revisit if CLI grows or another module starts depending on `desktopCli`.
