# Persistence

Small key-value persistence for user-facing toggles, device identity, and per-peer state. The backend choice (`androidx.datastore-preferences-core` directly in `commonMain`) and its rationale live in [ADR-persistence-key-value](adr/adr-persistence-key-value.md); this document is the contract every store and every backend wiring satisfies.

Out of scope: bulk or relational data, in-memory caches that don't survive process death, OS-level secret storage, and anything that needs transactional multi-key writes.

## When to add a store

A new store appears when a piece of state must survive process death and is not a cache. User-facing toggles, identity, and per-peer preferences qualify. Transient UI state, derived values, and request-scoped data do not.

A new method on an existing store is preferred over a new store class when the key fits the same concern (same eviction lifetime, same namespace, same audience). A new store is preferred when the concern is genuinely separate — different lifetime, different cleanup rule, or different layer of the app reads it.

## Store contract

A store is a typed `commonMain` class over an injected `DataStore<Preferences>`. It owns:

- **Its key namespace.** Keys are defined as private constants on the store and never leak. No store reads or writes another store's keys.
- **Its defaults.** The default value for a key lives at the key definition. A read of a never-written key returns the default; callers do not pass defaults.
- **Its serialisation.** Compound values are serialised by the store to a primitive `Preferences` type. The serialisation format is part of the on-disk schema and follows the same rule as key naming.

Reads return either a snapshot (`suspend fun get…(): T`) or a `Flow<T>` for observation; the flow emits the current value on subscription and a new value on every write in the same process. Writes are `suspend` and atomic per key. The store exposes only what callers need — a write-only key is not given a getter, an observed key is not given a snapshot getter unless something genuinely needs both.

```kotlin
// Pattern, not real names.
class SomePreferenceStore(private val dataStore: DataStore<Preferences>) {
    private val key = booleanPreferencesKey("foo_enabled")

    fun observeFoo(): Flow<Boolean> = dataStore.data.map { it[key] ?: DEFAULT_FOO }
    suspend fun setFoo(enabled: Boolean) { dataStore.edit { it[key] = enabled } }

    private companion object { const val DEFAULT_FOO = true }
}
```

## Key naming

Keys are stable strings. Once a key has been written to a user's device, its name is part of an implicit on-disk schema; renaming silently drops the user's value. New keys take a fresh name. Deprecated keys stay defined until a release explicitly removes them, so a migration step can read and clear them.

Per-peer keys take the form `<concern>:<peerId>`, where `peerId` is the stable device identifier that survives rename and re-pairing.

## Backend

One `DataStore<Preferences>` instance backs each on-disk preferences file; stores composed on the same concern share one instance. The instance is constructed at the per-platform composition root via `PreferenceDataStoreFactory.createWithPath`, using the platform-standard user data directory. Stores receive the instance by constructor injection and never construct or close a backend themselves.

A single live `DataStore` exists per on-disk file; constructing a second one against the same file is a programming error. Splitting state across multiple files is reserved for stores whose eviction lifetime, cleanup rule, or access-control boundary differs materially from general preferences.

## Cross-process observation

Reactive observation works within one process. A second process writing the same file is not observed by the first, and the contract does not promise it. Features that need cross-process reactivity (for example a share extension feeding the main app) re-open the backend decision for that store.

## Testing

Tests in `commonTest` construct a file-backed `DataStore` in `FileSystem.SYSTEM_TEMPORARY_DIRECTORY` with a `.preferences_pb` extension, using `PreferenceDataStoreFactory.createWithPath` and a dedicated coroutine scope separate from the system under test. Sharing the scope with the SUT lets SUT-side cancellation tear the store down mid-test. The test deletes the temp file and cancels the DataStore scope in `@AfterTest`; recreating a store on the same path within one test requires cancelling the previous scope first.

## Cross-cutting concerns

- **Identity.** Stores keyed by peer use the stable device identifier — the same string the trust layer uses, so per-peer preference survives device rename and re-pairing for the same reason trust does.
- **Lifecycle.** The backend is built once at app start and lives for the process lifetime. Stores hold it by reference.
- **Aging.** Nothing in this layer expires on its own. A store that needs eviction (for example forgetting a peer) deletes its own keys explicitly.
- **Placement.** Stores live in `commonMain` under the package of the concern they serve. Backend wiring lives in the per-platform composition roots, not in a separate persistence module.
- **Observability.** Reads and writes are silent. A store logs only on a backend error that changes user-visible behaviour (for example a swallowed deserialisation error that falls back to the default).
