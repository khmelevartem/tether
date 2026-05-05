# Dependency Injection

How Tether wires its components — today, and where this is headed. This is also the **"does my code fit?" checklist** for new code.

## Today: no DI

There is no DI framework. Components construct their own collaborators:

```kotlin
class FileClient { private val http = HttpClient { ... } }     // FileClient owns its http client
class FileServer { private val server = embeddedServer(...) }  // FileServer owns its server
```

This is fine while the project is tiny. It is also already starting to bite — see anti-patterns below.

## Decision

We adopt DI in two phases.

### Phase 1 (now): Manual DI via composition root

A single object — call it `AppGraph` — assembles dependencies in each platform's entry point:

- JVM: `Main.kt` builds `AppGraph`, hands components to whoever needs them.
- Android: `TetherApp.onCreate()` builds `AppGraph`, exposes it to `MainActivity`.
- iOS: `MainViewController` builds `AppGraph`, passes it into Compose.
- macOS: same shape as iOS.

Inside the graph, dependencies are wired by calling constructors. No reflection, no DSL, no annotations. Components themselves never call `new` on their collaborators.

### Phase 2 (later): Migrate to Metro

When manual DI becomes painful, we move to [Metro](https://github.com/ZacSweers/metro) — a Kotlin compiler-plugin DI framework by Zac Sweers (Anvil author). v1.0.0 stable, April 2026.

**Why Metro specifically:**

- KMP-first. Supports all our targets explicitly: `iosArm64`, `iosSimulatorArm64`, `macosArm64`, JVM, Android.
- Compile-time graph validation. Missing bindings fail the build, not the device.
- Compiler plugin (FIR/IR), not KSP — faster builds than `kotlin-inject`.
- Anvil-style `@ContributesBinding` / `@ContributesTo` fits the multi-module shape we're heading toward (see [modules.md](modules.md)).

**Why not Koin:** runtime resolution. In a KMP app where every target gets tested independently, "forgot to register a binding" turns into a runtime crash on the platform we tested last. We prefer compile-time errors.

**Why not kotlin-inject:** technically excellent, but KSP-based — slower build than a compiler plugin, and the multi-module ergonomics with `kotlin-inject-anvil` overlap with what Metro gives us natively.

**Why not Hilt / Dagger:** not KMP. Dagger generates JVM bytecode, Hilt is Android-locked.

### Migration trigger (when Phase 2 starts)

We move to Metro when **any one** of these hits:

- We split out a third Gradle module and wiring across module boundaries becomes tedious in `AppGraph`.
- We forget to register something for the first time and ship a crash to a platform.
- The composition root grows past ~30 lines or starts branching by platform / build flavor.

Until then, a compiler plugin is more apparatus than the problem deserves.

## Rules for new code (the "does it fit?" checklist)

These apply now, in Phase 1, and survive into Phase 2 unchanged.

### 1. Do not `new` your collaborators

❌
```kotlin
class FileClient {
    private val http = HttpClient { ... }   // owns HttpClient — can't be replaced in tests
}
```

✅
```kotlin
class FileClient(private val http: HttpClient) { ... }
```

The composition root creates the `HttpClient` once and hands it to every component that needs it. Tests construct a fake or test client.

### 2. Constructor injection only

No `lateinit var http: HttpClient` set after construction. No service-locator lookups inside methods (`AppGraph.http.send(...)`). Dependencies are explicit constructor arguments.

Reason: a class's constructor signature is its honest dependency list. Anything reached through globals is invisible.

### 3. Platform context is a dependency, not a global

❌
```kotlin
// MdnsDiscovery.android.kt
actual class MdnsDiscovery {
    private val nsd = TetherApp.context.getSystemService(...)  // global lookup
}
```

✅
```kotlin
actual class MdnsDiscovery(context: Context) {
    private val nsd = context.getSystemService(...)
}
```

The composition root in `TetherApp.onCreate()` already has `applicationContext` — pass it in.

### 4. One singleton, one owner

If a component is supposed to be a singleton (`HttpClient`, `FileServer`, `MdnsDiscovery`), it is created exactly once in the composition root. Don't put it in a Kotlin `object`. Don't lazy-init it from inside another component.

The composition root is the only place that knows lifecycles. Everywhere else, you receive what's already alive.

### 5. Don't introduce an interface for one implementation

This rule contradicts the cargo-cult version of Clean Architecture. We follow it consciously (see [architecture-principles.md](architecture-principles.md)). An interface earns its place when:

- there is a fake/test implementation, OR
- there are two real implementations (e.g. JVM and iOS file servers).

A `FileTransferRepository` interface with one `FileTransferRepositoryImpl` adds noise, not safety.

### 6. Test seams use fakes, not mocks of `HttpClient`

When you do need to test a class that depends on `HttpClient`, prefer Ktor's `MockEngine` or a hand-rolled fake — don't introduce a `HttpClientWrapper` interface just to mock it. Same logic for `MdnsDiscovery`: fake the `Flow<List<Device>>` it exposes, don't abstract the platform API behind another layer.

## Composition root sketch

This is the rough shape `AppGraph` will take in Phase 1. Not yet implemented; reference it when wiring is added.

```kotlin
// commonMain
class AppGraph(
    val protocol: ProtocolModule,
    val discovery: MdnsDiscovery,
    val client: FileClient,
)

// jvmMain (Main.kt)
fun main(args: Array<String>) {
    val graph = AppGraph(
        protocol = ProtocolModule(),
        discovery = MdnsDiscovery(),
        client = FileClient(HttpClient(CIO)),
    )
    val server = FileServer(/* ... */)
    // ...
}

// androidMain (TetherApp.onCreate)
appGraph = AppGraph(
    protocol = ProtocolModule(),
    discovery = MdnsDiscovery(applicationContext),
    client = FileClient(HttpClient(CIO)),
)
```

Things to notice:
- Different platforms build different graphs (Android passes `Context`, JVM also builds a `FileServer`).
- No component in the graph reaches across to construct another.
- `commonMain` defines the *shape* (`AppGraph` data class), platforms fill it in.

## Open questions

- When we get to Phase 2, do scopes (per-screen, per-transfer) make sense, or stick to a single application graph? Decide when the first scoped need appears.
- For per-screen view models, does Compose's `ViewModelStoreOwner` integration matter, or do we just instantiate them inside the graph? Address when 3+ screens exist.
- iOS receive-side: when a non-Ktor `FileServer` implementation lands, the graph branches per-platform more aggressively. May be the moment to flip to Metro.
