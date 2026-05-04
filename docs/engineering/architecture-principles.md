# Architecture Principles

We follow Clean Architecture **as a principle, not as a checklist.** Architecture serves the code; the code does not serve the architecture.

This document fixes our position so it doesn't drift over time.

## What we follow

**Stable abstractions inward, volatile details outward.** This is the only Clean Architecture rule we treat as load-bearing.

Concretely, this gives us a layering — from most stable to most volatile:

1. **Protocol / domain.** Pure Kotlin types and rules — what gets exchanged between devices and what's true about them. No platform, no framework. Changes when the network protocol or core domain rules change.
2. **Network / discovery / platform.** Transport, peer discovery, platform adapters. Depends on (1). Changes when we swap an underlying mechanism or add a target.
3. **Presentation.** ViewModels and the navigation graph. Depends on (2) through interfaces, not concretes. Owns UI state shape but not visual rendering.
4. **UI.** Compose composables. The thinnest layer — renders state, fires events. Treated as replaceable.

**Dependencies always point toward more stable code.** UI depends on the discovery interface. Discovery does not depend on UI. Protocol depends on nothing in this project except kotlinx.serialization.

**Platform-specific code stays at the edges.** `actual` implementations live in `androidMain` / `iosMain` / `appleMain` / `jvmMain` source sets. They are not referenced by name from `commonMain`.

## What we explicitly skip

Clean Architecture and adjacent patterns are full of ceremony that doesn't pay rent at our scale. We skip:

- **Use cases for everything.** A use-case class with a single `invoke()` that delegates to one repository method is noise. Add a use case only when there's *real* logic to host (orchestration, validation, multi-source coordination) — and only then.
- **Repositories layered over a single data source.** If there is one data source and one consumer, do not introduce a repository interface to "abstract" it. The interface comes when there's a second implementation (real / fake), or a real boundary worth defending.
- **Triple-layer DTO ↔ domain ↔ presentation mapping when there's no real divergence yet.** This one is nuanced: as the project matures, layers *do* legitimately need different shapes (DTO has serialization quirks, domain has invariants, presentation has display fields). The rule is not "never map" — it's "don't force three types up front." Start with one. Split when the second shape *actually* diverges (e.g. a UI list state with `selected`, `lastSeen`, `signal` is genuinely not the same as a network `Device`). Don't fight that growth in the name of brevity, and don't pre-empt it in the name of layering.
- **Interfaces for things with one implementation.** An interface earns its place when there are at least two implementations, or it's a seam for testing. Otherwise the concrete class is the contract.

## Heuristics for new code

When deciding whether to add a layer, an interface, or a use case, ask:

- **Would removing this layer make the code worse?** If no, don't add it.
- **What would I test against this seam?** If "nothing specific" — the seam is decorative.
- **Is the abstraction stable, or am I guessing?** Premature abstractions calcify the wrong shape. Concrete first, abstract on the second use case.

When refactoring existing code, the same questions apply in reverse: a layer that nobody benefits from can be deleted.

## Anti-patterns we have seen here

- Components that create their own collaborators (`HttpClient()` inside `FileClient`). Breaks testability and lifecycle. Fixed by constructor injection — see [dependency-injection.md](dependency-injection.md).
- Platform context (`TetherApp.context`) reached for from inside discovery/network code. Pushes platform details across layers. Fixed by passing what's needed explicitly into the platform-specific `actual`.
- UI directly orchestrating discovery + networking. Drag-bottom layer concerns into the most volatile one. Fixed by an `AppGraph` composition root that wires lifecycle, and a thin view-model surface for UI.

## Decisions

- **ViewModel lives in its own layer** between UI and the layers below it. Reason: keep Compose as a thin, replaceable UI framework — when we eventually adopt [Decompose](https://github.com/arkivanov/Decompose) for navigation and component lifecycle, the presentation layer is already isolated and the change is contained. Decompose itself is deferred until the screen count justifies it.
- **Unidirectional data flow** (state down, events up) is the default. The specific framework — MVI library, Molecule, plain Compose state — is chosen per component as it appears, not declared globally up front.

## Open questions

- None right now — revisit this section as new architectural choices come up.
