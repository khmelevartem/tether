# Layering

Tether's code is organised into four layers around a stable Domain core: **UI → Presentation → Domain ← Data**. Domain is the innermost, most stable ring; both Presentation and Data point inward to it, and UI points inward to Presentation. This document fixes what each layer owns, what it may import, what it must not, and how it talks to its neighbours.

It expands the load-bearing rule in [architecture-principles.md](architecture-principles.md): stable abstractions inward, volatile details outward. That document states the principle; this one states the per-layer contracts that follow from it.

## Goal

A reader can place any unit of code in exactly one layer, and decide whether a proposed import is allowed, without reading the rest of the codebase. The layering holds the same on every target: the layer a piece of code belongs to does not change with the platform it compiles for.

## The four layers

Every dependency points inward, toward Domain. UI names only Presentation; Presentation names only Domain; Data names only Domain. Nothing reaches outward, and nothing skips Domain to reach across it: Presentation and Data do not name each other — they meet only through the interfaces Domain owns. Stated the other way: the inner ring has no reference, by name or by type, to anything in a ring that depends on it.

### UI

Owns the rendered surface: composables that turn a state object into pixels and turn user gestures into events.

- **May import:** the presentation layer it renders, and the design-system / theme primitives.
- **Must NOT import:** the domain or data layers; no repositories, no transport, no platform adapters. A composable that needs a value reads it from the state object handed to it.
- **Talks to neighbours:** receives a plain state object downward, fires events as method calls upward to its presentation unit. It holds no business state and runs no business logic. Treated as replaceable — a ground-up UI rewrite changes nothing beneath it.

### Presentation

Owns the screen-shaped view of the world: units that map domain state into what a screen needs and relay user actions back down.

- **May import:** the domain layer only — domain types and the repository / collaborator interfaces the domain owns.
- **Must NOT import:** the data layer (no repository implementation, no transport, no persistence, no platform adapter — those are named only through the domain interfaces they satisfy), the UI layer (no composable, no rendering type), and platform actuals by name.
- **Talks to neighbours:** observes domain state through injected collaborators it names by their domain interface, exposes a single state value upward to the UI, relays user actions inward as plain calls. The concrete data-layer implementation behind each interface is supplied at the composition root by constructor injection; the presentation unit never sees the implementing type. Holds only view-state that would vanish under a different UI; the long-lived domain state, and the transition logic that drives it, live outside the unit and are reached through the injected domain interface. Detailed conventions, anatomy, and testing live in [presentation-layer.md](presentation-layer.md).

### Domain

Owns the invariants, rules, and state machines — what is true about the things Tether exchanges and how they may legally change. It also owns the repository and collaborator **interfaces**: the contracts through which an effect it cannot perform purely is requested. Presentation depends on these interfaces; Data implements them. The contract lives with the layer that defines what it means, not with the layer that fulfils it.

- **May import:** nothing in this project except its own types and pure Kotlin libraries (kotlinx.serialization is permitted for annotating domain types).
- **Must NOT import:** any platform API, any framework (no UI toolkit, no presentation primitive, no DI container), any transport or persistence type. An effect it cannot perform purely is declared as an interface here and implemented in the data ring.
- **Talks to neighbours:** offers types, operations, and interfaces the rings around it build on. It is the innermost and most stable ring and the one whose change is most expensive; it changes when the core rules of the product change, not when a mechanism or a screen changes.

### Data

Owns the volatile mechanisms: repository implementations, engines, transport, persistence, discovery, and the platform adapters behind them. The most volatile ring — it changes most when a mechanism is swapped or a target is added — yet it depends inward on Domain just as Presentation does, implementing the interfaces Domain declares.

- **May import:** the domain layer (to implement its interfaces and speak in domain types), and platform APIs in the appropriate source set.
- **Must NOT import:** the presentation or UI layers. A data unit never knows which screen observes it.
- **Talks to neighbours:** implements the interfaces the domain defines; a presentation unit reaches an implementation only through the domain interface, wired at the composition root. Platform-specific implementations live in platform source sets and are never referenced by name from `commonMain` — see [architecture-principles.md](architecture-principles.md).

## Rules that cut across the layers

- **No domain logic in presentation units.** If the logic must still exist when the UI is rewritten ground-up, it belongs in the domain layer. Presentation is restricted to mapping and relaying.
- **No view-state fields on domain types.** If a field would vanish under a different UI, it belongs to a presentation type that wraps the domain state, not to the domain type itself.
- **Long-lived state and the rules over it live outside presentation units, on opposite sides.** The logic that governs the state — the legal transitions, the state machine that drives them — is long-lived domain logic and belongs to the domain layer; it must still exist when the UI is rewritten. The persisted, observable state it produces is owned by a holder reached through a domain interface and wired at the composition root. A presentation unit owns neither: it observes the state through the injected domain interface and rebuilds, re-subscribes, and re-derives its view rather than retaining the state or re-implementing the transitions. Do not read "outlives the screen" as "belongs in the data layer" — transition logic that happens to be long-lived is still domain, and pushing it into a data-layer holder is the misplacement this rule forbids.
- **Constructors receive objects, not flows-of-objects to subscribe inside.** A unit is handed its collaborators ready to use; it does not reach out for them. See [dependency-injection.md](dependency-injection.md).
- **Dependencies point inward, always.** UI → Presentation → Domain ← Data. UI and Presentation form a chain toward Domain; Data joins from the other side, depending on Domain without Presentation ever depending on Data. Presentation and Data meet only through the interfaces Domain owns (see §Presentation and §Domain above). Nothing inner names anything that depends on it.

## Presentation ↔ Domain boundary: data SSOT, not code DRY

Two state properties that always answer the same question are one source of truth wearing two names — even when they live in different layers and have different types. Before introducing a new state property, verify no existing property already answers the same question under a different shape. If one does, derive the new view from the existing source rather than storing a second copy that another writer must remember to keep in step. A divergence between the two is then unrepresentable, because there is only one.

This is the data-level single-source-of-truth rule, and it is sharpest exactly at this boundary, where a domain fact is reshaped into a screen fact and the temptation is to persist the reshaped form as if it were independent. It is distinct from code-level DRY: collapsing duplicated *code* is a maintainability call the implementer makes locally; collapsing duplicated *state* is an architectural invariant, because mirrored state drifts and drifted state is a class of bug, not a style nit. The parent rule lives in [architecture-principles.md §Single source of truth — no mirror state](architecture-principles.md#single-source-of-truth--no-mirror-state).

## What this doc does not commit to

- The module boundaries that wrap these layers. A layer is a dependency rule; a module is a packaging unit. The current and target module split lives in [modules.md](modules.md).
- The framework backing the presentation layer, or the transport and persistence mechanisms backing the data layer. Those are mechanism choices recorded in their own living docs and ADRs; the layering holds whichever mechanism is in place.
- The number of types a fact passes through between layers. One shape until a second genuinely diverges — the layering says where a type may live, not how many must exist (see [architecture-principles.md](architecture-principles.md) on skipped ceremony).
