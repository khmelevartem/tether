# `Dispatchers.IO` on Kotlin/Native — extension-shadowed-by-member

On Apple targets in `kotlinx-coroutines-core` 1.7.0+, `Dispatchers.IO` is published — backed by a dedicated 64-thread pool wrapping the internal `DefaultIoScheduler`. But a naïve `Dispatchers.IO` reference from project code fails to compile:

```
e: Cannot access 'val IO: CoroutineDispatcher': it is internal in 'kotlinx.coroutines.Dispatchers'.
```

## Cause

The Native sources of `Dispatchers` declare the field as `internal`:

```kotlin
public actual object Dispatchers {
    public actual val Default: CoroutineDispatcher = ...
    public actual val Main: ...
    internal val IO: CoroutineDispatcher = DefaultIoScheduler   // member — internal
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public actual val Dispatchers.IO: CoroutineDispatcher get() = IO   // top-level extension
```

Kotlin's name resolution picks the member first, hits `internal`, errors out — never falls back to the public extension property declared in the same file.

## Workaround

Add an explicit import of the extension property; the compiler then resolves `Dispatchers.IO` to the extension, not the shadowed member.

```kotlin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO   // ← extension property

val IoDispatcher: CoroutineDispatcher = Dispatchers.IO
```

The same source file compiles for every target — on JVM `Dispatchers.IO` is a public member directly, the extension import is a no-op there.

## Why not write your own `expect/actual` wrapper

Tempting alternative: declare `expect val IoDispatcher` in `commonMain`, `actual val` per platform. That works, but it is a no-op wrapper over an upstream artifact that already does the platform split. Two extra files per concern, zero behavioural difference. The guard rail lives in [architecture-principles.md §Common-first](../engineering/architecture-principles.md) — check the upstream KMP artifact's KLIB targets before introducing your own `expect/actual`.

## Reference

- kotlinx-coroutines `nativeMain/Dispatchers.kt` — declarations of the internal member and the public extension.
- Upstream tracker for surfacing `Dispatchers.IO` cleanly: [KT-?] (no canonical issue found at time of writing; the `@Suppress("EXTENSION_SHADOWED_BY_MEMBER")` is the maintainers' acknowledged workaround).
