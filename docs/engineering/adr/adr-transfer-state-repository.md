# Transfer state — domain repository over a data-source seam

**Status:** Accepted — 2026-05-27
**Issue:** [#191](https://github.com/khmelevartem/tether/issues/191)

## Context

The transfer subsystem needs one place where per-peer transfer state lives. The state machine outlives any screen (a transfer started from the device list must stay visible after navigating to its details view and back), so it cannot live inside a Decompose Component. It must serialise concurrent mutations (a user can cancel a file while a batch is reporting progress while a `ReceiveEvent` is arriving from the network) and survive the gap between an outbound send finishing and the user dismissing the result.

Parent living doc: [file-transfer.md](../file-transfer.md). The wire-side contract is in [file-transfer-wire.md](../file-transfer-wire.md); this ADR is about the layer above it.

## Considered options

### Option 1 — Component-owned state

Each `PeerTransferComponent` owns its own `MutableValue<PeerTransferState>` and subscribes to `FileClient` / `FileServer` directly. No repository.

Closes: zero indirection, fewer files. Costs: state dies with the Component (back-navigation loses progress); two Components for the same peer drift; the Component imports transport types, breaking the layering rule.

### Option 2 — Presentation-layer repository over transport collaborators

`PeerTransferRepository` lives in `presentation.transfer` and depends directly on `FileClient` (outbound) and a `MutableSharedFlow<ReceiveEvent>` published by `FileServer` (inbound). `AppContainer` constructs both halves and the repository.

Closes: per-peer state survives Component rebuilds; one owner of mutation. Costs: the repository is misclassified — it holds domain state (per-peer transfer machines), not presentation state, so placing it in `presentation` puts a stable abstraction inside a volatile layer. `AppContainer` ends up exposing implementation details (the inbound flow, the sender factory) because the repository needs them through its constructor.

### Option 3 — Domain repository over a `PeerTransferDataSource` seam (chosen)

`PeerTransferRepository` lives in `com.tubetoast.tether.transfer` (domain). It depends on a `PeerTransferDataSource` interface that exposes a `Flow<ReceiveEvent>` for inbound and a per-peer `BatchSender` factory for outbound. The production data source — `TransportPeerTransferDataSource` in `com.tubetoast.tether.network` — adapts `FileClient` and `FileServer` to that interface. `AppContainer` wires `dataSource → repository → Components`; it does not expose either side of the seam.

Closes: the state machine is in the layer it conceptually belongs to; the repository is testable without any transport; `AppContainer` stops leaking implementation details; the data source is the natural extension point when persistent storage joins in-memory state.

Costs: one extra interface and one extra concrete class compared to Option 2.

## Decision

We place `PeerTransferRepository` in the domain layer and decouple it from the transport via a `PeerTransferDataSource` interface, with the production data source implemented as a transport adapter in the network layer.

This aligns with the standard Clean Architecture role of Repository (domain logic over data sources) and with our [architecture-principles.md](../architecture-principles.md) rule that stable abstractions live inward. The data source seam earns its place because there are real future implementations behind it: a persistent-storage variant once transfers need to survive process death, and a fake for repository tests today.

## Costs accepted

- One additional interface (`PeerTransferDataSource`) and one additional concrete class (`TransportPeerTransferDataSource`) compared to direct wiring.
- `PeerTransferState` carries two UI-flavoured fields (`Idle.expanded`, `Reconnecting.remainingSeconds`). Splitting into a domain core plus a presentation projection would force every other field to round-trip through a copy with no real divergence — the [architecture-principles.md](../architecture-principles.md) rule against pre-emptive triple-layer mapping applies. The split is deferred until a second view shape diverges.

## Consequences

- `AppContainer` exposes `peerTransferDataSource` and `peerTransferRepository`; it does not expose `inboundEvents`, sender factories, or any other implementation detail of the data source.
- Tests at the repository level inject a fake `PeerTransferDataSource` — a `MutableSharedFlow<ReceiveEvent>` and a `BatchSender`-returning lambda — without touching `FileClient` or `FileServer`.
- The `BatchSender`-to-`FileClient` wiring is the data source's responsibility, not the container's.
- When persistent storage for in-flight transfers is added, it joins as a second data source (or replaces the current one) without disturbing the repository or Components.

## Revisit if

- **A second view shape diverges from `PeerTransferState`.** Split into a domain state and a presentation projection then; do not pre-empt.
- **Two real data sources need to be composed** (e.g. in-memory + persistent). The repository may need a small composition layer; today's single-source case does not.

## References

- Parent living doc: [file-transfer.md](../file-transfer.md)
- Wire contract: [file-transfer-wire.md](../file-transfer-wire.md)
- [architecture-principles.md](../architecture-principles.md) — stable abstractions inward, no pre-emptive layer mapping
- [dependency-injection.md](../dependency-injection.md) — `AppContainer` as the only place that wires data source and repository
