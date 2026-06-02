# Layering

Tether's code is organised into four layers, ordered from most volatile to most stable: **UI → Presentation → Domain → Data**. This document fixes what each layer owns, what it may import, what it must not, and how it talks to its neighbours.

It expands the load-bearing rule in [architecture-principles.md](architecture-principles.md): stable abstractions inward, volatile details outward. That document states the principle; this one states the per-layer contracts that follow from it.

## Goal

A reader can place any unit of code in exactly one layer, and decide whether a proposed import is allowed, without reading the rest of the codebase. The layering holds the same on every target: the layer a piece of code belongs to does not change with the platform it compiles for.

## The four layers

Each layer depends only inward. An outer layer names the layer directly beneath it; it never reaches two layers down and never reaches outward. Identical phrasing in both directions: the inner layer has no reference, by name or by type, to anything in an outer layer.

### UI

Owns the rendered surface: composables that turn a state object into pixels and turn user gestures into events.

- **May import:** the presentation layer it renders, and the design-system / theme primitives.
- **Must NOT import:** the domain or data layers; no repositories, no transport, no platform adapters. A composable that needs a value reads it from the state object handed to it.
- **Talks to neighbours:** receives a plain state object downward, fires events as method calls upward to its presentation unit. It holds no business state and runs no business logic. Treated as replaceable — a ground-up UI rewrite changes nothing beneath it.

### Presentation

Owns the screen-shaped view of the world: units that map domain state into what a screen needs and relay user actions back down.

- **May import:** the domain layer, and data-layer repositories through inward-facing interfaces.
- **Must NOT import:** the UI layer (no composable, no rendering type), and platform actuals by name.
- **Talks to neighbours:** observes domain state through injected collaborators, exposes a single state value upward to the UI, forwards events downward as plain calls. Holds only view-state that would vanish under a different UI; long-lived domain state lives further in. Detailed conventions, anatomy, and testing live in [presentation-layer.md](presentation-layer.md).

### Domain

Owns the invariants, rules, and state machines — what is true about the things Tether exchanges and how they may legally change.

- **May import:** nothing in this project except its own types and pure Kotlin libraries (kotlinx.serialization is permitted for annotating domain types).
- **Must NOT import:** any platform API, any framework (no UI toolkit, no presentation primitive, no DI container), any transport or persistence type. If it needs an effect it cannot perform purely, it depends on an interface that an outer layer implements.
- **Talks to neighbours:** offers types and operations the layers around it build on. It is the most stable ring and the one whose change is most expensive; it changes when the core rules of the product change, not when a mechanism or a screen changes.

### Data

Owns the volatile mechanisms: repositories, engines, transport, persistence, discovery, and the platform adapters behind them. The outermost stable ring — outermost because it changes most when a mechanism is swapped or a target is added, stable in that it sits at the base of the dependency arrows.

- **May import:** the domain layer (to satisfy its interfaces and speak in domain types), and platform APIs in the appropriate source set.
- **Must NOT import:** the presentation or UI layers.
- **Talks to neighbours:** implements the inward-facing interfaces the domain defines, and exposes repositories the presentation layer observes. Platform-specific implementations live in platform source sets and are never referenced by name from `commonMain` — see [architecture-principles.md](architecture-principles.md).

## Rules that cut across the layers

- **No domain logic in presentation units.** If the logic must still exist when the UI is rewritten ground-up, it belongs in the domain layer. Presentation is restricted to mapping and relaying.
- **No view-state fields on domain types.** If a field would vanish under a different UI, it belongs to a presentation type that wraps the domain state, not to the domain type itself.
- **Long-lived state lives outside presentation units.** Anything that must outlive a single screen is owned in the data layer and observed through injection; presentation units rebuild and re-subscribe rather than retaining it.
- **Constructors receive objects, not flows-of-objects to subscribe inside.** A unit is handed its collaborators ready to use; it does not reach out for them. See [dependency-injection.md](dependency-injection.md).
- **Dependencies point inward, always.** Ordered from most volatile to most stable: UI → Presentation → Domain ← Data. Presentation also imports Data (see §Presentation above). Nothing inner names anything outer.

## Presentation ↔ Domain boundary: data SSOT, not code DRY

Two state properties that always answer the same question are one source of truth wearing two names — even when they live in different layers and have different types. Before introducing a new state property, verify no existing property already answers the same question under a different shape. If one does, derive the new view from the existing source rather than storing a second copy that another writer must remember to keep in step. A divergence between the two is then unrepresentable, because there is only one.

This is the data-level single-source-of-truth rule, and it is sharpest exactly at this boundary, where a domain fact is reshaped into a screen fact and the temptation is to persist the reshaped form as if it were independent. It is distinct from code-level DRY: collapsing duplicated *code* is a maintainability call the implementer makes locally; collapsing duplicated *state* is an architectural invariant, because mirrored state drifts and drifted state is a class of bug, not a style nit. The parent rule lives in [architecture-principles.md §Single source of truth — no mirror state](architecture-principles.md#single-source-of-truth--no-mirror-state).

## What this doc does not commit to

- The module boundaries that wrap these layers. A layer is a dependency rule; a module is a packaging unit. The current and target module split lives in [modules.md](modules.md).
- The framework backing the presentation layer, or the transport and persistence mechanisms backing the data layer. Those are mechanism choices recorded in their own living docs and ADRs; the layering holds whichever mechanism is in place.
- The number of types a fact passes through between layers. One shape until a second genuinely diverges — the layering says where a type may live, not how many must exist (see [architecture-principles.md](architecture-principles.md) on skipped ceremony).
