# File Transfer

Tether's file-transfer subsystem owns one job: take a list of file sources picked on one device and land them as files on a paired peer, surviving brief network drops and reporting per-file outcomes. This document fixes the layering and the seams. The on-the-wire HTTP contract is in [file-transfer-wire.md](file-transfer-wire.md). The architectural decision that fixed the layering is in [adr/adr-transfer-state-repository.md](adr/adr-transfer-state-repository.md).

## Layering

Three layers, ordered from most stable to most volatile, per [architecture-principles.md](architecture-principles.md):

1. **Domain** — `com.tubetoast.tether.transfer`. Protocol-shaped types (`FileSource`, `PeerIdentity`, `ReceiveEvent`, `BatchProgress`, `PerFileStatus`, `FailureReason`, `TransferErrorReason`, `PartialOutcome`), the batch orchestrator (`BatchSender`), the per-peer state machine (`PeerTransferState`, `Direction`), and the contracts that mediate between them (`PeerTransferRepository`, `PeerTransferDataSource`). No platform, no transport, no Compose.
2. **Transport adapters** — `com.tubetoast.tether.network`. The HTTP shapes (`FileClient`, `FileServer`) and the data source that adapts them to the domain (`TransportPeerTransferDataSource`).
3. **Presentation** — `com.tubetoast.tether.presentation.transfer`. Decompose Components that observe per-peer state and forward user intent to the repository.

Dependencies point inward: presentation depends on domain interfaces; transport adapters depend on domain types; domain depends on nothing in this project except kotlinx.

## The per-peer state machine

`PeerTransferState` is a sealed type modelling one peer's transfer lifecycle: `Idle`, `ActiveOutbound`, `ActiveInbound`, `Reconnecting`, `Sent`, `Received`, `Cancelled`, `Error`. The repository owns one `MutableStateFlow<PeerTransferState>` per peer; Components observe.

State is domain, not presentation. The same argument that places discovered devices in the domain applies here: filtering, aggregation, and persistence (when added) are domain concerns. Two fields lean view-shaped — `Idle.expanded` (whether the per-peer card is opened on the device list) and `Reconnecting.remainingSeconds` (banner countdown). They stay on the domain type by pragmatism: a separate presentation wrapper today would carry every other field unchanged. When a second view shape genuinely diverges (e.g. a tablet detail layout demanding different fields), split then — not pre-emptively.

## The seam — `PeerTransferDataSource`

`PeerTransferRepository` does not talk to `FileClient` or `FileServer`. Between them sits `PeerTransferDataSource`, the seam that lets the repository be tested without a transport and lets the transport evolve (in-memory now, persistent later) without disturbing the state machine.

The data source exposes two surfaces:

- **Inbound** — `Flow<ReceiveEvent>`, the stream of receive events produced when this device acts as the server. The repository collects this stream and folds events into per-peer state.
- **Outbound** — a per-peer factory for `BatchSender`, the orchestrator the repository launches when the user starts a send.

Production wiring binds inbound events to the `FileServer` callback and outbound senders to a `BatchSender` built around `FileClient`. Tests bind a fake.

This is the Clean Architecture data-source role: the repository mediates between domain logic and one or more data sources; the data source is replaceable. See the [Repository](../glossary.md#technical) and [DataSource](../glossary.md#technical) glossary entries.

## Ownership and lifecycle

`AppContainer` constructs `TransportPeerTransferDataSource` and `PeerTransferRepository` as singletons; it does not own transfer state, event streams, or sender factories. The repository's coroutine scope is `appScope` — process-lived. Components hold no transfer state; they observe.

Concurrency rule: the repository serialises all mutations of its per-peer maps under a single `Mutex`. Launched batches publish progress back through the same lock-guarded state flows. Active job and current-sender references are reset only when the launched job is still the active one — late completions from cancelled jobs do not stomp fresh state.

## Adding a new transfer event

When the protocol grows a new inbound event:

1. Add the variant to `ReceiveEvent` in the domain.
2. Map it in `PeerTransferRepository.handleInbound` to the appropriate state transition.
3. Emit it from the receiving side (`FileServer` route handler → `TransportPeerTransferDataSource`).
4. Add a `commonTest` case that publishes the event into a fake data source and asserts the resulting `PeerTransferState`.

The `FileClient` / `FileServer` wire contract grows only when the new event needs a new HTTP signal — most do not.

## Testing

- **Repository** — `commonTest`. Construct with a fake data source: a `MutableSharedFlow<ReceiveEvent>` as `inboundEvents`, a function returning a hand-built `BatchSender` for `outboundSender`. Drive inbound by emitting; drive outbound by calling repository methods and stepping `runCurrent()`.
- **Components** — `commonTest` with `FakePeerTransferRepository`. The Component never sees a data source.
- **`BatchSender`** — `commonTest` directly. The sender is a pure orchestrator; tests pass a `sendOne` lambda and a `FakeConnectionMonitor`.

Patterns and helpers (fakes, `runTest`, virtual time) follow [testing.md](testing.md).
