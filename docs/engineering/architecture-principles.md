# Architecture Principles

We follow Clean Architecture **as a principle, not as a checklist.** Architecture serves the code; the code does not serve the architecture.

This document fixes our position so it doesn't drift over time.

## What we follow

**Stable abstractions inward, volatile details outward.** This is the only Clean Architecture rule we treat as load-bearing.

Concretely, this gives us a layering around a stable Domain core. Domain is the innermost ring; Presentation and Data both depend inward on it, and UI depends inward on Presentation:

1. **Domain.** Pure Kotlin types, rules, and the repository / collaborator interfaces — what gets exchanged between devices, what's true about them, and the contracts through which effects are requested. No platform, no framework. The most stable ring; changes when the core domain rules change.
2. **Presentation.** Components (Decompose) and the navigation graph. Depends on Domain only — domain types and the interfaces it owns. Owns UI state shape but not visual rendering. Changes when a screen changes.
3. **UI.** Compose composables. The thinnest layer — renders state, fires events. Treated as replaceable.
4. **Data.** Repository implementations, engines, transport, peer discovery, persistence, and platform adapters. Depends on Domain, implementing its interfaces. The most volatile ring; changes when we swap an underlying mechanism or add a target.

Per-layer ownership, allowed imports, and the data-flow direction between neighbours are spelled out in [layering.md](layering.md).

**Dependencies always point inward, toward Domain.** UI imports only from Presentation; Presentation imports only from Domain — domain types and the interfaces Domain owns, never a data-layer type. Data imports from Domain, implementing those interfaces. Domain depends on nothing in this project except kotlinx.serialization. Presentation and Data never name each other; the concrete implementation behind each interface is supplied at the composition root by constructor injection. Per-layer import rules: [layering.md](layering.md).

**Platform-specific code stays at the edges.** `actual` implementations live in `androidMain` / `iosMain` / `appleMain` / `jvmMain` source sets. They are not referenced by name from `commonMain`.

## What we explicitly skip

Clean Architecture and adjacent patterns are full of ceremony that doesn't pay rent at our scale. We skip:

- **Use cases for everything.** A use-case class with a single `invoke()` that delegates to one repository method is noise. Add a use case only when there's *real* logic to host (orchestration, validation, multi-source coordination) — and only then.
- **Repositories layered over a single data source.** If there is one data source and one consumer, do not introduce a repository interface to "abstract" it. The interface comes when there's a second implementation (real / fake), or a real boundary worth defending.
- **Triple-layer DTO ↔ domain ↔ presentation mapping when there's no real divergence yet.** This one is nuanced: as the project matures, layers *do* legitimately need different shapes (DTO has serialization quirks, domain has invariants, presentation has display fields). The rule is not "never map" — it's "don't force three types up front." Start with one. Split when the second shape *actually* diverges (e.g. a UI list state with `selected`, `lastSeen`, `signal` is genuinely not the same as a network `Device`). Don't fight that growth in the name of brevity, and don't pre-empt it in the name of layering.
- **Interfaces for things with one implementation.** An interface earns its place when there are at least two implementations, or it's a seam for testing. Otherwise the concrete class is the contract.
- **Migration scaffolding before there is anything to migrate from.** Format converters, schema-upgrade paths, deprecated-flag fallbacks, version-skew shims all wait until the first shipped version creates real legacy. Pre-MVP, format changes propagate by wipe-and-reinstall on debug devices — that's the explicit cost of not yet having users.

## Common-first across KMP targets

Everything that can live in `commonMain` lives there; platform source sets are for code that requires platform API only. This applies to everything — domain, network, presentation, UI. An implementation in `commonMain` ships to all active targets from a single codebase.

- Compilation ≠ correctness: code runs on all targets, but visual / runtime correctness on each is not guaranteed by the build. **A manual smoke test is mandatory on every platform** before requesting review (`/implement` Step 7).

**UI specifically** is the most visible multiplier: Compose Multiplatform in `commonMain` ships to Android, iOS, and Desktop (when the entry point calls `App()`) as a single implementation.

Per-platform composables / `actual` implementations are the exception and require justification by a real API constraint (system share sheet, hardware sensor), not by preference.

**Before writing your own `expect`/`actual`, check whether an upstream KMP artefact already covers it.** If a library already ships `expect val` / `expect class` (`Dispatchers.IO`, `androidx.datastore-preferences-core`, …) — use it directly, do not wrap it. Your own `expect`-wrapper on top of someone else's is a no-op layer that merely hides that the problem is already solved. The trigger for your own `expect/actual` is a platform API that genuinely has no library coverage — not "consistency within this project".

## Domain identity over display labels

If a domain entity has an `id`, every layer operates on the entity by id — lookup, eviction, dedup, filter. Display labels (names, titles) exist only at the UI edge. Collisions on labels are normal and not the domain's problem to forbid; the first layer that falls back to a name-key introduces a class of bugs that then propagates by copy-paste to every sibling adapter. Adapters reconstruct id at the boundary; if a platform callback genuinely cannot, that's a constraint to call out — not a license to add a label-keyed API to common code.

## Single source of truth — no mirror state

When state has a designated owner (store, repository, service), other layers do not keep their own parallel copy. Adapters translate between native shapes and the owner's API on the fly; long-lived mirror maps drift, because every event has to remember to update both, and readers that hit the mirror see stale data.

Before introducing a new state property, verify no existing property already answers the same question under a different name or shape; if yes, derive one from the other. This is most critical at the Presentation↔Domain boundary — see [layering.md](layering.md#presentation--domain-boundary-data-ssot-not-code-dry).

## A guarded state transition reports whether it happened

When a method guards a state machine — accepts the request only from certain states, ignores it otherwise — it returns whether the transition occurred (`Boolean`, or a richer result), not `Unit`. A `Unit`-returning guard forces every caller to know the internal guard condition and re-derive it before acting on the outcome; that knowledge crosses the function boundary and rots. The caller then performs follow-up side effects (clearing a buffer, navigating, clearing pending files) on the assumption the transition happened — and when the guard silently refused, the side effect runs anyway against a state that never changed.

Make the acceptance observable at the call site: `fun startOutbound(...): Boolean` returns `false` when the state machine refuses, and the caller branches on it. This converts a class of "fire-and-forget across a guard" bugs into a compile-time obligation to handle the refusal.

## Every node is both client and server

Every running Tether instance is symmetric on the transport: it hosts an HTTP server and is an HTTP client to its peers. Any device can send to any device; there is no designated host, no asymmetry between a "server" device and a "client" device.

This is a product invariant, not an implementation convenience. The product is peer-to-peer ([vision.md](../product/vision.md)): two people on the same network connect their devices directly. A client/server split would require electing a host — which contradicts the discovery model (every peer announces and is discoverable on equal footing) and the home-network use case (no fixed machine is "the server"). The server role and the client role both run on every node at once; neither is optional.

The network-stack ADR ([adr/adr-network-stack.md](adr/adr-network-stack.md)) takes this symmetry as a premise for its engine choice; this principle is the canonical home for the invariant itself.

## Named classes over anonymous objects

When you need to implement an interface — even with a trivial body that's just data (e.g. a config interface filled with constants) — **prefer a named class in its own file** over `object : Interface { ... }` inline.

Why:
- Anonymous objects sprout duplicates. The next call site copies the literal, drifts on a field, and the divergence is invisible at review.
- A named class has a place in the file tree and a name in stack traces and test reports.
- Refactoring (rename a config field, add a new one) updates one file instead of N inline objects.

The exception is one-shot test fakes for a single test method, where extracting would obscure the test. If the same anonymous object would be useful in a second test — extract.

## Naming: spell properties out, no abbreviations

Don't shorten property or local-variable names to save keystrokes (`srv`, `disc`, `cfg`, `mgr`). Names should describe what the value is, not approximate it.

When two values represent the same conceptual entity in different roles (e.g. a candidate held in a local before being published to a field), don't disambiguate by abbreviating one of them. Pick names that describe each role: a "freshly fetched" instance vs a "started/attached" one, an "incoming" vs a "current" instance. The reader should learn from the name why both exist.

Reason: abbreviated names hide intent and merge with each other (`svc`, `srv`, `srvc` all blur together). Descriptive names also surface duplication that abbreviations let slide — if two variables would have the same long name, that's a design signal, not a styling problem.

## Heuristics for new code

When deciding whether to add a layer, an interface, or a use case, ask:

- **Would removing this layer make the code worse?** If no, don't add it.
- **What would I test against this seam?** If "nothing specific" — the seam is decorative.
- **Is the abstraction stable, or am I guessing?** Premature abstractions calcify the wrong shape. Concrete first, abstract on the second use case.

When refactoring existing code, the same questions apply in reverse: a layer that nobody benefits from can be deleted.

## Anti-patterns we have seen here

- Components that create their own collaborators. Breaks testability and lifecycle. The remedy is constructor injection — see [dependency-injection.md](dependency-injection.md).
- Platform context (`TetherApp.context`) reached for from inside discovery/network code. Pushes platform details across layers. Fixed by passing what's needed explicitly into the platform-specific `actual`.
- UI directly orchestrating discovery + networking. Drag-bottom layer concerns into the most volatile one. Fixed by an `AppContainer` composition root that wires lifecycle, and a thin Component surface for UI (see [presentation-layer.md](presentation-layer.md)).

## Decisions

- **Presentation layer is built on [Decompose](https://github.com/arkivanov/Decompose).** Components hold state and lifecycle in plain Kotlin; Compose subscribes via `subscribeAsState` and is treated as a thin, replaceable renderer. Conventions and how to write/test components: [presentation-layer.md](presentation-layer.md). Rationale, alternatives considered, and per-platform notes: [adr/adr-presentation-and-navigation.md](adr/adr-presentation-and-navigation.md).
- **Unidirectional data flow** (state down, events up) is the default. The specific framework — MVI library, Molecule, plain Compose state — is chosen per component as it appears, not declared globally up front.

## Sanity-checking architectural calls against prior art

When a conceptual / architectural call touches OS limits, transport, or other platform-level constraints — and the answer is unclear from Apple / Android / JVM docs alone — cross-check against [LocalSend](https://github.com/localsend/localsend) as a secondary signal. LocalSend is the closest open-source architectural analog (cross-platform LAN P2P file transfer); their issue tracker has accumulated years of hitting the same OS walls Tether faces.

**Use it for:** "is this OS constraint really unavoidable, or did we miss a workaround?" Maintainer positions on architectural issues (e.g., iOS background networking) are prior-art-grade signal — a different team independently reaching the same conclusion strengthens confidence that the wall is real.

**Do NOT use it for feature parity.** Tether is not LocalSend; their UX, scope, and product decisions are theirs. Borrowing implementation choices for transport / discovery / platform integration is fine when the choice is architecturally constrained anyway. Borrowing product features dilutes Tether's positioning.

**Trigger sparingly.** Conceptual hard cases only, not routine implementation questions.

## Open questions

- None right now — revisit this section as new architectural choices come up.
