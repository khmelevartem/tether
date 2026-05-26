# Key-value persistence — `androidx.datastore-preferences-core` in commonMain

**Status:** Accepted — 2026-05-26
**Issue:** [#190](https://github.com/khmelevartem/tether/issues/190)

## Context

Tether needs a small key-value persistence layer for device identity, per-peer toggles, global file-transfer preferences, and future stores of similar shape. The UI subscribes to most of these, so reads must be observable as `Flow`; writes are `suspend` and atomic per key. The bulk of store logic — namespacing, defaults, compound-value serialisation — belongs in `commonMain`, with the backend constructed once at the per-platform composition root. Out of scope: bulk or relational data, large blobs, OS-level secret storage.

Parent living doc: [`persistence.md`](../persistence.md).

## Decision drivers

| Driver | Why it matters for Tether |
|---|---|
| KMP target coverage | Single artifact must cover Android, JVM Desktop (Windows / Linux / macOS), and iOS. |
| Native `Flow` + `suspend` API | Stores expose reactive reads; rewriting per-store `Flow`-on-write wrappers per platform is the cost we refuse to pay. |
| Library count | Each external dependency is maintenance surface; fewer is better when one library covers the need. |
| Common-code testability | Backend must be constructible in `commonTest` so store contracts test once. |
| Android backend stance | The recommended Android backend for key-value preferences is `androidx.datastore`; `SharedPreferences` is no longer the recommended option. |

## Considered options

### Option A — `androidx.datastore:datastore-preferences-core` directly in `commonMain`

`androidx.datastore-preferences-core` is published as a Kotlin Multiplatform artifact covering Android, JVM, iOS (arm64 / simulator-arm64 / x64), macOS, and Linux. The API is the same `DataStore<Preferences>` everywhere: `data: Flow<Preferences>` for observation, `edit { … }` for atomic suspend writes. A single backend instance per file is constructed at the composition root via `PreferenceDataStoreFactory.createWithPath` (with the platform-standard data directory) and injected into stores. Stores in `commonMain` depend only on `DataStore<Preferences>`; no `expect/actual` per store.

### Option B — `multiplatform-settings` family (`-core` + `-datastore` + `-coroutines` + `-test`)

A `Settings` / `ObservableSettings` abstraction with pluggable backends, including a `DataStoreSettings` backend wrapping the same `DataStore<Preferences>`. Once the backend is fixed to DataStore on every target, the abstraction adds four artifacts to wrap an API we already want to use directly. `Settings.putString` / `putBoolean` is also less idiomatic than `dataStore.edit { it[KEY] = value }` for compound updates. The family pays for itself when different platforms must use different backends; Tether does not need that.

### Option C — Per-store `expect/actual` over platform KV APIs

A common interface per store with an actual class per platform, each wrapping `SharedPreferences` / `NSUserDefaults` / `java.util.prefs.Preferences` and reimplementing a `Flow`-on-write wrapper. Zero external dependencies. Costs N×3 boilerplate, hand-written change listeners per platform, and pins the Android side to `SharedPreferences` against current Android guidance.

## Decision

**We use `androidx.datastore:datastore-preferences-core` directly in `commonMain` as the single key-value backend.** The library ships the exact `Flow` + `suspend` API stores need on every target Tether supports, removing the motivation for any wrapper abstraction or per-store platform code.

## Costs accepted

- **Non-Android targets of `datastore-preferences-core` are labelled experimental by Google**, while the artifacts are published and stable in practice. We accept the risk of API breaks across major versions on iOS / JVM / macOS / Linux; the Android target is stable.
- **No in-memory `DataStore` is provided.** Common tests construct a file-backed `DataStore` in a temp directory with a separate coroutine scope; the pattern is documented in [`persistence.md`](../persistence.md).
- **Cross-process write observation is not guaranteed.** A second OS process writing the same file is not part of the contract; reactive observation works within a single process.

## Consequences

- Stores in `commonMain` take an injected `DataStore<Preferences>` and own their key namespace, defaults, and any compound-value serialisation.
- Each per-platform composition root constructs the backend via `PreferenceDataStoreFactory.createWithPath` using the platform-standard data directory (Android — `Context.preferencesDataStoreFile(name)`-equivalent path; Apple — app sandbox path; JVM Desktop — per-OS user data directory).
- Adding a new preference is a method on an existing store or a new store class in `commonMain`; no new `expect/actual` pair, no new dependency.

## Revisit if

- **`androidx.datastore` non-Android KMP targets introduce a breaking change** that we cannot absorb cheaply. Pin to the last working version and re-evaluate.
- **Cross-process write observation becomes a requirement** (for example an iOS share extension writing a preference the main app must react to). Re-evaluate the backend for that store.
- **A store needs transactional multi-key writes across files, fully-encrypted-at-rest storage, or large blobs.** That store adopts a different backend; this ADR remains in force for everything else.

## References

- Upstream: [`androidx.datastore:datastore-preferences-core` on Google Maven](https://maven.google.com/web/index.html#androidx.datastore:datastore-preferences-core).
- AndroidX KMP guide: [DataStore on Kotlin Multiplatform](https://developer.android.com/kotlin/multiplatform/datastore).
- Living doc: [`persistence.md`](../persistence.md).
