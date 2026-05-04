# Architecture Principles

We follow Clean Architecture **as a principle, not as a checklist.** Architecture serves the code; the code does not serve the architecture.

This document fixes our position so it doesn't drift over time.

## What we follow

**Stable abstractions inward, volatile details outward.** This is the only Clean Architecture rule we treat as load-bearing.

Concretely, this gives us a layering — from most stable to most volatile:

1. **Protocol / domain** — `protocol/Device.kt`, transfer envelopes, sendable types. Pure Kotlin, no platform, no framework. Changes when the network protocol itself changes.
2. **Network / discovery / platform** — `FileServer`, `FileClient`, `MdnsDiscovery`, `Platform`. Depends on (1). Changes when we adopt a new transport, swap an mDNS implementation, or add a target.
3. **UI** — Compose screens, view models, navigation. Depends on (2) through interfaces, not concretes. Changes most often.

**Dependencies always point toward more stable code.** UI depends on the discovery interface. Discovery does not depend on UI. Protocol depends on nothing in this project except kotlinx.serialization.

**Platform-specific code stays at the edges.** `actual` implementations live in `androidMain` / `iosMain` / `appleMain` / `jvmMain` source sets. They are not referenced by name from `commonMain`.

## What we explicitly skip

Clean Architecture and adjacent patterns are full of ceremony that doesn't pay rent at our scale. We skip:

- **Use cases for everything.** A use-case class with a single `invoke()` that delegates to one repository method is noise. Add a use case only when there's *real* logic to host (orchestration, validation, multi-source coordination) — and only then.
- **Repositories layered over a single data source.** If there is one data source and one consumer, do not introduce a repository interface to "abstract" it. The interface comes when there's a second implementation (real / fake), or a real boundary worth defending.
- **Triple-layer DTO ↔ domain ↔ presentation mapping** when one type works fine. We start with one type and split when divergence appears, not in anticipation of it.
- **Interfaces for things with one implementation.** An interface earns its place when there are at least two implementations, or it's a seam for testing. Otherwise the concrete class is the contract.

## Heuristics for new code

When deciding whether to add a layer, an interface, or a use case, ask:

- **Would removing this layer make the code worse?** If no, don't add it.
- **What would I test against this seam?** If "nothing specific" — the seam is decorative.
- **Is the abstraction stable, or am I guessing?** Premature abstractions calcify the wrong shape. Concrete first, abstract on the second use case.

When refactoring existing code, the same questions apply in reverse: a layer that nobody benefits from can be deleted.

## Anti-patterns we have seen here

- Components that `new` their own collaborators (`HttpClient()` inside `FileClient`). Breaks testability and lifecycle. Fixed by constructor injection — see [dependency-injection.md](dependency-injection.md).
- Platform context (`TetherApp.context`) reached for from inside discovery/network code. Pushes platform details across layers. Fixed by passing what's needed explicitly into the platform-specific `actual`.
- UI directly orchestrating discovery + networking. Drag-bottom layer concerns into the most volatile one. Fixed by an `AppGraph` composition root that wires lifecycle, and a thin view-model surface for UI.

## Open questions

- Where does ViewModel live in our hierarchy — UI layer or its own "presentation" layer? Not enough screens yet to decide. Revisit when we have 3+.
- Do we adopt a unidirectional data flow convention (MVI / Molecule / Compose state) explicitly, or stay flexible? Decide once UI surface grows.
