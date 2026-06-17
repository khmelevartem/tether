# Manual DI in Kotlin Multiplatform: a composition root instead of a framework

You can ship a Kotlin Multiplatform app without a DI framework. No reflection, no code generation, no annotations, no DSL. Dependency injection comes down to one idea: there is a single place where shared components are constructed — the composition root — and every other class receives its dependencies through its constructor.

The key idea is KMP-specific and structural: the container hierarchy mirrors the source-set hierarchy. A component's place is decided by which API its source set can see — the structure answers "where do I construct this?" for you. I haven't seen this framed in the manual-DI writing out there: it's either presented as a stepping stone to a framework, or described outside any KMP context.

This isn't a refusal of the tool on principle. A framework solves real problems, and on a large project it pays for itself. But it also brings a compiler plugin or KSP, its own build lifecycle, its own rules, and its own learning curve. Until the problems it solves actually show up, it's more apparatus than the task deserves. In this article I'll walk through how manual DI works in [Tether](https://github.com/khmelevartem/tether) — a KMP file-transfer app for Android, iOS, and Desktop — and where the honest line sits, past which a framework finally earns its place.

The code in the examples is synthetic: class names are generalized to show the shape of the pattern, not a specific implementation. The patterns themselves come from production code.

## Prerequisites

For the pattern to make sense, you need three things.

- A Kotlin Multiplatform project with a source-set hierarchy: shared code in `commonMain`, platform code in `androidMain`, `iosMain`, `jvmMain`, and so on.
- One place in the app that holds the constructed dependencies and owns their lifecycle.
- Dependencies passed through constructors by default, not pulled out of global state.

I'll unpack each of these — first at the app level, then how the same approach scales to standalone KMP libraries.

## The core idea: a composition root

The composition root is the single place where the app assembles its dependency graph. I call it the container. The container constructs shared components once and hands them to everyone who needs them. Nobody but the container calls a shared component's constructor.

```kotlin
class AppContainer {
    val httpClient: HttpClient by lazy { HttpClient() }
    val messageRepository: MessageRepository by lazy { MessageRepository(httpClient) }
    val syncService: SyncService by lazy { SyncService(messageRepository) }
}
```

Each component receives its dependencies through the constructor. The container knows the assembly order and owns the singletons. `SyncService` doesn't know where `MessageRepository` came from — it just accepts it. That's dependency injection: not "the framework supplied it," but "the caller passed it."

The payoff shows up immediately in tests. Because `SyncService` takes `MessageRepository` through the constructor, a test passes a fake there — no mocks, no reflection, no swapping globals.

### This is DI, not a service locator

A common objection to manual DI: in practice it degrades into a service locator — a global registry that classes reach into for their dependencies. The objection misses. The difference is the direction of control: with a service locator the class knows about the registry and pulls dependencies from it; with constructor injection the class knows of no container at all — the composition root passes dependencies into it. The container here isn't a registry to read from, it's where assembly happens. [Martin Fowler](https://martinfowler.com/articles/injection.html) drew this line in detail, and it's the same line that separates real DI from its imitation.

The one place a class does reach for the container itself is Android components with empty constructors. That's an explicit, fenced concession — not a general way to fetch dependencies; covered below.

## The container hierarchy mirrors the source-set hierarchy

A KMP app doesn't have one container, it has a tree. And that tree mirrors the source-set tree. Each layer adds only what its source set can actually see.

![The container hierarchy mirrors the source-set hierarchy](manual-di-source-set-hierarchy.png)

```
AppContainer            (commonMain)
├── JvmAppContainer     (jvmMain)
│   ├── AndroidAppContainer  (androidMain)
│   └── DesktopAppContainer  (desktopMain)
└── AppleAppContainer   (appleMain)
    └── IosAppContainer      (iosMain)
```

`AppContainer` in `commonMain` assembles everything expressible in shared code. `AndroidAppContainer` in `androidMain` adds components that need a `Context` or some other Android API. `IosAppContainer` adds whatever requires Apple frameworks. A platform leaf inherits the common container and fills in what's missing.

The boundary runs exactly along API visibility. If a component is built from shared abstractions, it lives in the common container. If it needs a platform type, it drops into the platform leaf. The container tree answers "where do I construct this component?" mechanically: where the required API first becomes visible.

## Configuration: the container's input

The container needs values only the entry point knows: the server port, the downloads directory, a reference to the Android `Application`. These are gathered into a separate config object whose hierarchy mirrors the container hierarchy.

```kotlin
// commonMain
interface AppConfig {
    val downloadsDir: String
    val port: Int
}

// androidMain
interface AndroidAppConfig : AppConfig {
    val application: Application
}
```

The container takes the config through its constructor. `AppContainer` in `commonMain` is no-arg; intermediate containers take their `AppConfig` subtype; leaves pass a concrete config up.

Config is the input. The container is what gets assembled from it. The split gives you exactly one place where the app sets parameters, and a separate place where the graph is built from them.

Concrete config implementations are named classes in their own files, not anonymous `object : AppConfig { … }` literals. An anonymous object at the call site invites drift between assembly points: two places construct an "almost identical" config and quietly diverge. A named class is the single source of truth.

## Platform entry points

Each platform builds its own container leaf in its own entry point and hands components down.

- On **Android**, the container is built in the `Application` subclass and lives as long as the process.
- On **iOS**, the container is built in the UI entry point — but outside the `Composable` lambda, so the composable doesn't become a composition root (more on this below).
- On **Desktop**, there can be several entry points: a GUI launcher and a CLI one, say. Each builds its own container leaf, and only what it needs ends up on each compilation's classpath.

The entry point knows the lifecycle. Everything else receives components that are already alive.

## The Provider pattern for Android

Android comes with a specific complication. Its framework-managed components — `Activity`, `Service`, `BroadcastReceiver` — are constructed by the framework and must have empty constructors. You can't pass them the container through the constructor like a normal class.

The pattern that fits this situation:

1. The `Application` subclass implements a provider interface and owns the container.
2. Framework components reach the container through a cast: `(application as AppContainerProvider).container`.

![The Provider pattern for Android components](manual-di-android-provider.png)

```kotlin
interface AppContainerProvider {
    val container: AppContainer
}

class MyApp : Application(), AppContainerProvider {
    override val container: AppContainer by lazy { AndroidAppContainer(/* config */) }
}

class MyService : Service() {
    private val container by lazy { (application as AppContainerProvider).container }
}
```

This is not a service locator, even though it looks like one. The difference is discipline: the provider interface is implemented by exactly one type (the production `Application`, and a test one in tests), and only Android-framework-managed classes reach the container through it. Plain Kotlin classes still receive their dependencies through the constructor. Static access is an explicit, unavoidable concession to empty constructors — not a way to pull anything from anywhere.

The pattern itself is Android's own official recommendation for manual DI ([Manual dependency injection](https://developer.android.com/training/dependency-injection/manual)). The docs frame it as a stepping stone to Hilt; here it stays a standalone solution exactly until the triggers in the final section fire.

## The rules: does my code fit?

A composition root works as long as the code around it follows a handful of rules. This is both a checklist for new code and a definition of what even counts as a dependency.

**Constructor injection only — don't `new` your collaborators.** A class's constructor is its honest list of dependencies. Anything it pulls from globals or constructs internally is invisible on that list.

```kotlin
// bad: the class owns its collaborator, can't be replaced in a test
class SomeService {
    private val repo = SomeRepository()
}

// good: the dependency is explicit
class SomeService(private val repo: SomeRepository)
```

Same trap, a nullable dependency defaulting to `= null`. If the production build always supplies the collaborator but the signature makes it optional, a caller that forgets it silently disables the feature with no compile error. Make it required. Reserve the `null` default for a genuinely optional dependency — and then test the null branch.

**Platform context is a dependency, not a global.** The entry point already holds the application `Context`. Pass it into the constructor instead of reaching into a static field.

```kotlin
// bad
actual class SomeService {
    private val manager = GlobalApp.context.getSystemService(/* … */)
}

// good
actual class SomeService(context: PlatformContext) {
    private val manager = context.getSystemService(/* … */)
}
```

**One singleton, one owner.** If a component is meant to be a singleton, it's constructed exactly once — in the composition root. Not in an `object`, not lazily from deep inside another component. The root is the only thing that knows lifecycles; everywhere else you receive what's already alive.

**Don't construct dependencies inside a composable.** A composable that calls a service constructor itself is a composition root in the wrong place.

```kotlin
// bad: a new instance on every recomposition, leak
@Composable
fun SomeScreen() {
    val service = SomeService(SomeRepository())
    DisposableEffect(Unit) {
        service.start()
        onDispose { service.stop() }
    }
}
```

`DisposableEffect(Unit)` runs once and captures the instance from the first composition. On every later recomposition a new `SomeService` is created and immediately discarded — never started, never stopped. Even `remember` here fixes the leak but not the real mistake: the composable owns a singleton's lifecycle, which it shouldn't. The correct shape is to build the container in the entry point and pass the ready component in.

**Don't introduce an interface for one implementation.** An interface earns its place when there's a fake for tests or a second real implementation (different file servers on JVM and iOS, say). A `SomeRepository` interface with a single `SomeRepositoryImpl` adds noise, not safety. This is a deliberate departure from the cargo-cult version of Clean Architecture, where an interface is created for every class just in case.

**For test seams, use fakes, not wrapper interfaces.** When a class does need testing, write a fake by hand instead of introducing an interface just to mock it. A mock framework forces a layer of indirection that leaks into production code. If a fake is hard to write because the component is too coupled to its collaborators, fix the component.

**The container holds named components, not plain data.** A container field is an object that owns or manages something: a store, a server, a client, a scope, a factory. Not a string, a number, a port, or a tag. Values like an identity, an alias, or a port belong to the component that owns them; a consumer receives the component and asks for the value at the moment it needs it — including across an async boundary, if the value is only known after I/O. Plain data on the container forces it to materialize at construction time, hides who's responsible for it, and turns the dependency graph into a property bag.

**No silently-throwing common defaults.** When a container field needs a per-platform implementation, the common contract says so explicitly: the field is `abstract` on the common container, so every platform leaf is forced to provide a value or it won't compile. A concrete common default that throws and works only because one platform happened to override it is the worst case: it compiles, passes fake-based tests, and crashes at runtime on the platform that forgot.

## Testability

The container is also the seam tests use to substitute fakes. For this to work, container fields are declared as `open val`: overriding is the substitution mechanism. No setters, no `lateinit var`.

```kotlin
class FakeContainer : AppContainer() {
    override val httpClient = FakeHttpClient()
    override val messageRepository = FakeMessageRepository()
}
```

A test builds a fake container by subclassing and overriding the fields it needs. Components that take dependencies through the constructor are tested directly — they don't need the container at all; it exists for production wiring.

To test Android components under Robolectric you need an `Application` that implements the provider interface. Either the production `Application`, or a test one with a fake container — and each test gets a fresh instance, nothing settles into a static.

## How this scales to libraries

While the app is a single module, the composition root stays flat. But the same approach grows to standalone KMP libraries, and there a second cut appears: what the library exposes versus what it hides.

### Splitting into Api and Impl

A library splits into two modules. The **Api** module holds only interfaces and models for external use. The **Impl** module depends on Api and holds all the implementations.

The only app module that depends on **Impl** is the one where DI is assembled. Every other app module and other libraries depend on **Api** only. The single class from **Impl** a consumer touches is the library's entry point.

![Wiring Api and Impl](manual-di-api-impl.png)

Why the cut: it strictly marks the library's public API and, as a result, thins the integration layer. A consumer sees exactly what the library promised and can't accidentally lean on an implementation detail.

### Public and internal containers

A library's container forks by visibility.

- **LibPublicContainer** — the library's public API (its façade). It holds every dependency the library hands to external consumers.
- **LibInternalContainer** — a subtype of the public one that adds dependencies for internal use. External consumers don't see them.

The app stores the constructed container and uses it through the public interface. The library's own internal classes use the extended one.

![Public and internal containers](manual-di-public-internal.png)

```kotlin
// Api module
interface LibPublicContainer {
    val someRepository: SomeRepository
}

// Impl module
interface LibInternalContainer : LibPublicContainer {
    val someInternalService: SomeInternalService
}
```

### The library entry point

A library has one entry point — `LibModule`. Its sole responsibility is to create the container. It does nothing else.

```kotlin
// Api module
interface LibModule {
    fun create(config: LibConfigContainer): LibPublicContainer
}
```

The app implements the config interface `LibConfigContainer` and passes it into `create`. The method returns the container as the public interface; internally the library knows it's the full container with the internal part.

```kotlin
class App {
    val lib: LibPublicContainer by lazy {
        LibModuleImpl.create(AndroidLibConfig(application = this))
    }
}
```

From there, the ready container is simply passed into consumers' constructors:

```kotlin
val someInteractor: SomeInteractor by lazy {
    SomeInteractor(repository = lib.someRepository)
}
```

### Static access from inside the library

Sometimes the library's own internal classes need the container statically rather than through a constructor — the same story as Android components. The library then declares a provider interface, and the app implements it.

```kotlin
// in the library (Api module)
interface LibProvider {
    val lib: LibPublicContainer
}

// in the app
class App : Application(), LibProvider {
    override val lib: LibPublicContainer by lazy {
        LibModuleImpl.create(AndroidLibConfig(application = this))
    }
}

// inside the library
class LibActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val internal = (application as? LibProvider)?.lib as? LibInternalContainer
        internal?.someInternalService?.start()
    }
}
```

### The platform container

If a library has many platform dependencies, it's convenient to extract them into a separate `LibPlatformContainer` and assemble the container by composition rather than inheritance. It's an optional element: with few platform dependencies, declare them right in `LibInternalContainer` and implement them in its platform leaves.

### Initialization order

A library's container is assembled in steps.

![Library DI initialization order](manual-di-init-order.png)

1. The app implements the config interface `LibConfigContainer` and passes it into `LibModule.create`.
2. The library creates the platform implementation of `LibPlatformContainer` — its own internal entity.
3. From the config and the platform container, the library assembles the full container implementing both the public and internal interfaces.
4. The app stores the container and exposes it through `LibProvider`.
5. The app uses the container through the public interface; classes inside the library use the internal one.

Same discipline as in the app. The module does nothing but create the container. The containers implement no extraneous interfaces: the config and the platform container that landed in the constructor are implementation details, not part of the public façade.

## When a framework finally earns its place

Manual DI pays off up to a certain scale. Past it, it starts to hurt — and then moving to a framework is a deliberate decision, not a defeat.

The line runs along concrete triggers. I move to a DI framework when any one of these hits:

- a third Gradle module gets split out, and wiring dependencies across module boundaries in the container becomes tedious;
- I forget to register a dependency for the first time and ship a crash to a platform;
- a container leaf grows past a reasonable limit or starts branching by build flavor.

Until one of those fires, a compiler plugin is more apparatus than the problem deserves.

What matters is that the container hierarchy and the provider pattern map onto a framework's shape almost one to one. The container becomes a dependency graph, the platform subclasses become platform contributions to the graph, the config interfaces become value providers. Tests that overrode `open val`s become test graphs with overridden bindings. There's no structural reshuffle — which is exactly why deferring the move is cheap.

Picking the specific framework is a separate conversation, and the criteria for KMP are its own. For a Kotlin Multiplatform project I look first at:

- **support for every target platform** — iOS targets, JVM, Android all explicitly supported;
- **compile-time graph validation** — a forgotten dependency should fail the build, not crash at runtime on the platform tested last;
- **build speed** — a compiler plugin is usually faster than KSP;
- **multi-module ergonomics** — how the framework wires contributions from different modules.

Runtime-resolution solutions lose on the very first criterion for KMP: in an app where every target is tested separately, "forgot to register" turns into a device crash instead of a compile error.

By those criteria my pick today is [Metro](https://github.com/ZacSweers/metro): KMP-first, validates the graph at compile time, and runs as a compiler plugin rather than through KSP. Koin loses on the second criterion — runtime resolution. kotlin-inject is technically excellent but KSP-based, so it loses on build speed. Dagger and Hilt aren't KMP at all. But Metro is the destination here, not the starting point: until a trigger fires, it's apparatus the problem doesn't yet need. The one thing that matters is that the crossover point is visible in advance and cheap to reach.

## Why this approach pays off

- **No apparatus before its time.** No plugin, no code generation, no runtime resolution. The dependency graph is ordinary Kotlin code, read and debugged like any other.
- **Honest constructors.** A class's signature is its full dependency list. Nothing arrives from an invisible global.
- **The structure answers "where do I construct this."** The container tree mirrors the source-set tree, and a component's place is determined mechanically — where the required API first becomes visible.
- **A thin integration layer for libraries.** The Api/Impl split strictly marks the public surface.
- **A cheap move to a framework.** When the triggers fire, the shape already matches a graph's — relocate without a structural reshuffle.

Composition over magic. Until the project outgrows it, you'll have exactly as much DI as you need — and not a line more.

A manual composition root for KMM is not a brand-new idea — Marcin Piekielny sketched a frameworkless SDK container with a public/internal split back in [2023](https://medium.com/@maruchin/kmm-architecture-5-dependency-injection-79052c7ea778). What this article adds is the parts that make it hold up past toy size: the container hierarchy mirroring the source-set tree across the full platform set, the Android provider pattern, the named framework-adoption triggers, and the scaling path to libraries.

The code this article is based on is open source: [github.com/khmelevartem/tether](https://github.com/khmelevartem/tether). The full breakdown of the pattern and the checklist for new code live in `docs/engineering/dependency-injection.md`.
