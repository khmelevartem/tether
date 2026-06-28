package com.tubetoast.tether.transfer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Fans out [InboundEvent]s from the FileServer to per-peer [ReceiveEvent] flows consumed by
 * [PeerTransferEngine]. Synthesizes [ReceiveEvent.Started] and [ReceiveEvent.BatchCompleted]
 * which have no direct wire equivalent (see `docs/engineering/file-transfer-wire.md`).
 *
 * **Batch-end heuristic:** each file arrives as a separate HTTP request with no manifest.
 * [ReceiveEvent.BatchCompleted] is synthesized only on a deliberate receiver cancel
 * ([InboundEvent.ConnectionLost.cancelled] = true); a genuine network drop emits only
 * [ReceiveEvent.ConnectionLost], which drives the engine into Reconnecting and then (on
 * timeout) to Error(NetworkLost). Adding a wire-level batch manifest is tracked in #507.
 */
class InboundEventRouter(
    scope: CoroutineScope,
    inboundEvents: SharedFlow<InboundEvent>,
) {
    private val peerFlows = mutableMapOf<PeerIdentity, MutableSharedFlow<ReceiveEvent>>()
    private val peerFilesSeen = mutableMapOf<PeerIdentity, MutableList<String>>()
    private val peerFilesDone = mutableMapOf<PeerIdentity, Int>()

    init {
        scope.launch {
            inboundEvents.collect { event -> route(event) }
        }
    }

    fun eventsFor(peer: PeerIdentity): SharedFlow<ReceiveEvent> = flowFor(peer).asSharedFlow()

    private fun flowFor(peer: PeerIdentity): MutableSharedFlow<ReceiveEvent> =
        peerFlows.getOrPut(peer) {
            MutableSharedFlow(replay = 0, extraBufferCapacity = 64)
        }

    private fun route(event: InboundEvent) {
        val peer = event.peer
        val flow = flowFor(peer)
        val seen = peerFilesSeen.getOrPut(peer) { mutableListOf() }

        when (event) {
            is InboundEvent.FileStarted -> {
                if (event.name !in seen) seen += event.name
                flow.tryEmit(
                    ReceiveEvent.Started(
                        currentFile = event.name,
                        totalFiles = seen.size,
                    ),
                )
            }
            is InboundEvent.Progress -> {
                flow.tryEmit(
                    ReceiveEvent.Progress(
                        name = event.name,
                        receivedBytes = event.receivedBytes,
                        totalBytes = event.totalBytes,
                    ),
                )
            }
            is InboundEvent.FileCompleted -> {
                peerFilesDone[peer] = (peerFilesDone[peer] ?: 0) + 1
                flow.tryEmit(ReceiveEvent.FileCompleted(name = event.name))
            }
            is InboundEvent.Failed -> {
                flow.tryEmit(ReceiveEvent.Failed(file = event.name, reason = event.reason))
            }
            is InboundEvent.ConnectionLost -> {
                val received = peerFilesDone[peer] ?: 0
                val total = seen.size.coerceAtLeast(received)
                if (event.cancelled) {
                    flow.tryEmit(ReceiveEvent.BatchCompleted(received = received, total = total))
                }
                flow.tryEmit(ReceiveEvent.ConnectionLost(receivedSoFar = received))
                peerFilesSeen.remove(peer)
                peerFilesDone.remove(peer)
            }
        }
    }
}
