# Architecture Principles

We follow Clean Architecture **as a principle, not as a checklist.** Architecture serves the code; the code does not serve the architecture.

This document fixes our position so it doesn't drift over time.

## What we follow

**Stable abstractions inward, volatile details outward.** This is the only Clean Architecture rule we treat as load-bearing.

Concretely, this gives us a layering — from most stable to most volatile:

1. **Protocol / domain.** Pure Kotlin types and rules — what gets exchanged between devices and what's true about them. No platform, no framework. Changes when the network protocol or core domain rules change.
2. **Network / discovery / platform.** Transport, peer discovery, platform adapters. Depends on (1). Changes when we swap an underlying mechanism or add a target.
3. **Presentation.** Components (Decompose) and the navigation graph. Depends on (2) via constructor injection. Owns UI state shape but not visual rendering.
4. **UI.** Compose composables. The thinnest layer — renders state, fires events. Treated as replaceable.

**Dependencies always point toward more stable code.** UI depends on the discovery interface. Discovery does not depend on UI. Protocol depends on nothing in this project except kotlinx.serialization.

**Platform-specific code stays at the edges.** `actual` implementations live in `androidMain` / `iosMain` / `appleMain` / `jvmMain` source sets. They are not referenced by name from `commonMain`.

## What we explicitly skip

Clean Architecture and adjacent patterns are full of ceremony that doesn't pay rent at our scale. We skip:

- **Use cases for everything.** A use-case class with a single `invoke()` that delegates to one repository method is noise. Add a use case only when there's *real* logic to host (orchestration, validation, multi-source coordination) — and only then.
- **Repositories layered over a single data source.** If there is one data source and one consumer, do not introduce a repository interface to "abstract" it. The interface comes when there's a second implementation (real / fake), or a real boundary worth defending.
- **Triple-layer DTO ↔ domain ↔ presentation mapping when there's no real divergence yet.** This one is nuanced: as the project matures, layers *do* legitimately need different shapes (DTO has serialization quirks, domain has invariants, presentation has display fields). The rule is not "never map" — it's "don't force three types up front." Start with one. Split when the second shape *actually* diverges (e.g. a UI list state with `selected`, `lastSeen`, `signal` is genuinely not the same as a network `Device`). Don't fight that growth in the name of brevity, and don't pre-empt it in the name of layering.
- **Interfaces for things with one implementation.** An interface earns its place when there are at least two implementations, or it's a seam for testing. Otherwise the concrete class is the contract.

## Common-first across KMP targets

Всё, что может жить в `commonMain` — там и лежит; платформенные source sets только для кода, требующего platform API. Это применимо ко всему — domain, network, presentation, UI. Реализация в `commonMain` поставляется на все активные таргеты одной кодовой базой.

- Компиляция ≠ корректность: код едет на все таргеты, но визуальная/runtime-корректность на каждом не гарантирована билдом. **Ручной smoke обязателен на каждой платформе** перед запросом ревью (`/implement` Step 7 / `/work-on-issue` Step 7).

**UI specifically** — самый видимый multiplier: Compose Multiplatform в `commonMain` едет на Android, iOS и Desktop (когда entry point вызывает `App()`) одной имплементацией.

Per-platform composables / `actual`-имплементации — исключение и требуют обоснования реальным API-ограничением (system share sheet, hardware sensor), не предпочтением.

## Domain identity over display labels

If a domain entity has an `id` (synthetic or derived — e.g. `"$name@$host:$port"`), **every** layer that operates on the entity operates by id: lookup, eviction, equality, dedup, filter, cross-reference. Names, titles, captions and other human-readable labels exist only at the UI rendering edge.

Why: collisions on display labels are normal (two devices named "MacBook", two files called "report.pdf") and are not the domain's problem to forbid. The first layer to fall back from id to name introduces a class of bugs — same-name collisions, rename-loses-reference, label-impersonation — that then propagates by copy-paste to every sibling adapter. A single id-only contract makes that class of bugs unrepresentable.

Practical consequences:
- Native callbacks that only carry a label (e.g. `serviceName` in mDNS lost-events, file names in filesystem watchers) must be **translated to id at the adapter boundary**, not propagated upward as a name-key. If the native event carries enough information to reconstruct the id, reconstruct it. If it doesn't, that's a platform constraint to call out explicitly — not a license to add a name-keyed API to common code.
- Store / repository APIs accept domain models or ids, never display labels.
- Self-filter, dedup, eviction operate on id-tuples (or on (host, port) / equivalent identity-bearing slices), not on names.

## Single source of truth — no mirror state

When a piece of state has a designated owner (a store, a repository, a service), other layers do **not** keep their own parallel copy of it. Adapters translate between native callback shapes and the owner's API on the fly; they do not maintain long-lived mirror maps that need their own invalidation and resync logic.

Why: a mirror state is a second source of truth waiting to drift. Every event that mutates the owner must also remember to mutate the mirror; every reader who hits the mirror instead of the owner sees stale data; tests written against the mirror don't catch real-world divergence. The owner's API is the contract — adapters consume it.

Common smell: `nameToDevice` / `idToEntity` / `pendingFoo` maps living in adapter classes alongside a Store that already holds the same data. If the adapter genuinely cannot derive the answer from the owner (because the platform callback drops the identifying field — see "Domain identity" above), the fix is to reconstruct identity inside the callback, not to shadow the store.

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

- Components that create their own collaborators (`HttpClient()` inside `FileClient`). Breaks testability and lifecycle. Fixed by constructor injection — see [dependency-injection.md](dependency-injection.md).
- Platform context (`TetherApp.context`) reached for from inside discovery/network code. Pushes platform details across layers. Fixed by passing what's needed explicitly into the platform-specific `actual`.
- UI directly orchestrating discovery + networking. Drag-bottom layer concerns into the most volatile one. Fixed by an `AppContainer` composition root that wires lifecycle, and a thin Component surface for UI (see [presentation-layer.md](presentation-layer.md)).

## Decisions

- **Presentation layer is built on [Decompose](https://github.com/arkivanov/Decompose).** Components hold state and lifecycle in plain Kotlin; Compose subscribes via `subscribeAsState` and is treated as a thin, replaceable renderer. Conventions and how to write/test components: [presentation-layer.md](presentation-layer.md). Rationale, alternatives considered, and per-platform notes: [adr/adr-presentation-and-navigation.md](adr/adr-presentation-and-navigation.md).
- **Unidirectional data flow** (state down, events up) is the default. The specific framework — MVI library, Molecule, plain Compose state — is chosen per component as it appears, not declared globally up front.

## Open questions

- None right now — revisit this section as new architectural choices come up.
