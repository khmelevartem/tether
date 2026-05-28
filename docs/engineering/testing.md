# Testing

Tests are mandatory. When implementing any functionality, write unit and/or integration tests — use the edge cases from the issue as a guide.

**Behaviour fix — regression test in the same commit.** If a commit fixes an observable behaviour (bug, regression, non-feature), the same commit must include a test that would have failed without the fix. This applies equally to BUGFIX issues and to bugs found mid-flight on a FEATURE task (manual user test, review, smoke). If the regression test costs more than the fix itself — that is a signal that the testability seam needs to be improved in the same change (extract a function, move a dependency into a parameter), not deferred.

**Assert at the stable external seam.** Behavioural invariants — concurrency rules, lifecycle bookkeeping, ordering, error mapping — assert at the public API of the unit under test (constructor / DI entry point used in production, exposed flows, returned values, emitted events), not at internals. Tests bound to private state, internal classes, or reflection pay a refactor tax: every layer move or rename rewrites them. Tests at the external seam survive refactors as long as the contract holds, which is the test's job to check. If a test cannot be expressed at the external seam without touching internals, the seam is wrong — fix the seam, not the test.

A test lives in the most general source set across whose targets every referenced type has an identical signature; types with platform-divergent signatures (different constructor, different `expect/actual` shape) force the test into a more specific source set.

## Design

- **Fakes over mocks.** Mocks that pin call order are brittle and rewrite themselves on every refactor. A fake that implements the contract survives. Reach for a mock only when the dependency has no behaviour worth faking (record-and-replay HTTP, opaque external SDK).
- **One behaviour per test.** The test name describes the behaviour, not the implementation. Multiple asserts are fine when they describe the same behaviour from different angles; multiple behaviours in one test hide which one broke.
- **Mental inversion.** Before submitting, invert the production code under test in your head — would the test fail? If not, the test is a tautology. A test that passes regardless of production logic is worse than no test: it manufactures false confidence.
- **Isolation.** No shared mutable static state between tests. Each test sets up its own fixtures and runs independently under CI parallelism.

## Style

- `kotlin.test`.
- For coroutines — `runTest` + `TestDispatcher` (from `kotlinx-coroutines-test`), **not `runBlocking`**.
- Control time virtually via `advanceTimeBy()` / `advanceUntilIdle()` — this speeds tests up and makes them deterministic.
- The `tether:no-run-blocking-in-tests` rule (`:ktlint-rules`) automatically forbids `runBlocking` in test source sets. For legitimate integration tests on real threads, add `@Suppress("ktlint:tether:no-run-blocking-in-tests")` — on the class when the entire class is an integration suite around a single external API (real CIO server, JmDNS), even if individual tests in that class do not use `runBlocking`; on the function when an integration method sits inside a class that is not an integration suite by nature. Alongside the suppress — a `//` comment naming the specific real-thread event being awaited.

## Real time vs virtual time

- `Thread.sleep` and `System.currentTimeMillis`-polling are permissible **only** when waiting for events from external native APIs (JmDNS, NsdManager, etc.) that run on real threads outside our `CoroutineScope`. Everything else inside the test body uses virtual time.
- `withTimeout` inside `runTest` uses **virtual** clocks even on `Dispatchers.IO` — do not rely on it as a real-time timeout.

## Apple targets

NSRunLoop must be pumped manually — see [`docs/knowledge/apple-platform.md`](../knowledge/apple-platform.md) for details.

## Test seams

When an implementation cannot produce an error condition without mocking a platform API (e.g. DataStore does not throw on demand), extract the dependency behind an interface and supply a throwing anonymous object in tests:

```kotlin
val throwingStore = object : TrustedDeviceStore {
    override suspend fun isTrusted(deviceId: String) = false
    override suspend fun saveTrustedKey(deviceId: String, publicKey: ByteArray) =
        throw IllegalStateException("simulated write failure")
    override suspend fun getPublicKey(deviceId: String): ByteArray? = null
}
```

The interface flows into the production DI graph; the anonymous object replaces it in the specific test without touching the graph shape. This is the pattern used to verify the HTTP `/pair → 500` contract in `FileServerPairTest`.

Do not do this preemptively — only when the error path is unreachable from the real implementation and the end-to-end contract (e.g. HTTP status) must be verified. An interface earns its place as a test seam per `architecture-principles.md §What we explicitly skip`.

## HTTP client in unit tests

A class holding an `HttpClient` accepts it via constructor; the production config is built in `companion object { fun default() }`. The test passes `HttpClient(MockEngine)` with a handler that responds to requests.

To make `delay()` in the handler and the client's internal timers obey the `TestCoroutineScheduler` under `runTest`, pin the dispatcher on the engine:

```kotlin
private fun TestScope.httpFor(handler: ...): HttpClient =
    HttpClient(MockEngine) {
        engine {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler(handler)
        }
    }
```

Without `dispatcher = ...`, handlers spin up their own engine dispatcher (real-time) — `runTest`'s virtual time is ignored, and the test becomes either slow or flaky.

A real CIO server (`embeddedServer(CIO)`) does **not** submit to virtual time: `CIOApplicationEngine` hard-codes `userDispatcher = Dispatchers.IOBridge` and wraps route handlers in `withContext(userDispatcher)`. If a test specifically requires a real CIO server — that is integration-level; keep it in `FileServerTest` with `runBlocking` and real time.

## Removing tests

Removing tests is undesirable — they protect invariants, some of which are not obvious from the test name. Before removing:

1. List every invariant the test was checking (not only those in the name).
2. For each, state what protects it after removal: another test, a type contract, a code property.
3. If even one invariant is left unprotected — either do not remove the test, or add protection (test / type / assertion) in the same commit.

This inventory is a mandatory part of the commit message / PR description when removing a test.

Alternatives to removal: `@Ignore` with a link to a tracking issue (test is temporarily broken), simplifying the test (too heavy), moving it to a separate source set (platform-specific).

## Screenshot tests

Roborazzi renders every `@Preview` composable to a PNG via Robolectric — no emulator required. ComposablePreviewScanner discovers all `@Preview` functions in `com.tubetoast.tether` via bytecode reflection; one generic parameterised test in `composeApp/src/androidUnitTest/` covers all of them without per-preview boilerplate.

**Record PNGs** (initial capture or after intentional visual change):

```bash
./gradlew :composeApp:recordRoborazziDebug -q
```

PNGs land in `composeApp/build/outputs/roborazzi/`. Filenames encode the composable's FQN and the `@Preview` `name` parameter. `review-visual` reads these PNGs and compares them against the UX brief; baseline-diffing in CI is out of scope.

`captureRoboImage` is a no-op outside the `record*` / `verify*` / `compare*` Roborazzi tasks (which set `-Proborazzi.test.record=true` etc.), so `./gradlew allTests` and pre-commit hooks do not pay the Robolectric cold-start cost for screenshot rendering.

**Rules for new `@Preview`s:**
- Always target the stateless `XxxContent(state, callbacks)` variant of a composable, never the `XxxScreen(component)` wrapper. The wrapper depends on Decompose and cannot render under Robolectric.
- Wrap every preview in `PreviewSurface { }` from `com.tubetoast.tether.ui.preview` for consistent theme + background.
- Use fake state from `PreviewFixtures` in the same package — no inline data fabrication.

## Running

```bash
./gradlew allTests -q                                    # all tests; pre-commit / pre-push hooks run them automatically
./gradlew :composeApp:desktopTest -q                     # Desktop JVM only
./gradlew :composeApp:commonTest -q                      # common only
./gradlew :composeApp:desktopTest --tests "com.tubetoast.tether.network.FileServerTest"
```
