@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.tubetoast.tether.transfer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicReference

/**
 * Fans out [InboundEvent]s from the FileServer to per-peer [ReceiveEvent] flows consumed by
 * [PeerTransferEngine], and to the aggregate [receiveEvents] flow consumed by platform notifiers.
 *
 * Synthesizes [ReceiveEvent.Started] on [InboundEvent.BatchStarted] (or on the first file event
 * for senders that skip the batch-begin call), and [ReceiveEvent.BatchCompleted] when the
 * per-peer file completion count reaches the declared total.
 *
 * A sender that posts to /upload without a preceding /batch-begin is treated as an implicit
 * single-file batch: [ReceiveEvent.Started] is synthesized before the file's events and
 * [ReceiveEvent.BatchCompleted] fires after the single file completes.
 *
 * All mutations to the per-peer batch state run on the single collector coroutine; the
 * per-peer flow map is an immutable snapshot updated via CAS so engines can register from
 * any thread without coordination with the collector.
 */
class InboundEventRouter(
    scope: CoroutineScope,
    inboundEvents: SharedFlow<InboundEvent>,
) {
    private val peerFlows = AtomicReference(emptyMap<PeerIdentity, MutableSharedFlow<ReceiveEvent>>())
    private val peerBatch = mutableMapOf<PeerIdentity, PeerBatch>()

    private val _receiveEvents = MutableSharedFlow<Pair<PeerIdentity, ReceiveEvent>>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    val receiveEvents: SharedFlow<Pair<PeerIdentity, ReceiveEvent>> = _receiveEvents.asSharedFlow()

    init {
        scope.launch {
            inboundEvents.collect { event -> route(event) }
        }
    }

    fun eventsFor(peer: PeerIdentity): SharedFlow<ReceiveEvent> = flowFor(peer).asSharedFlow()

    private fun flowFor(peer: PeerIdentity): MutableSharedFlow<ReceiveEvent> {
        while (true) {
            val current = peerFlows.load()
            val existing = current[peer]
            if (existing != null) return existing
            val newFlow = MutableSharedFlow<ReceiveEvent>(replay = 0, extraBufferCapacity = 64)
            val next = current + (peer to newFlow)
            if (peerFlows.compareAndSet(current, next)) return newFlow
        }
    }

    private fun emit(peer: PeerIdentity, flow: MutableSharedFlow<ReceiveEvent>, event: ReceiveEvent) {
        flow.tryEmit(event)
        _receiveEvents.tryEmit(peer to event)
    }

    private fun route(event: InboundEvent) {
        val peer = event.peer
        val flow = flowFor(peer)

        when (event) {
            is InboundEvent.BatchStarted -> {
                val batch = peerBatch[peer]
                if (batch == null || batch.batchId != event.batchId) {
                    peerBatch[peer] = PeerBatch(batchId = event.batchId, totalFiles = event.totalFiles)
                    emit(peer, flow, ReceiveEvent.Started(currentFile = "", totalFiles = event.totalFiles))
                }
            }
            is InboundEvent.FileStarted -> {
                ensureImplicitBatch(peer, flow)
            }
            is InboundEvent.Progress -> {
                emit(
                    peer,
                    flow,
                    ReceiveEvent.Progress(
                        name = event.name,
                        receivedBytes = event.receivedBytes,
                        totalBytes = event.totalBytes,
                    ),
                )
            }
            is InboundEvent.FileCompleted -> {
                val batch = peerBatch[peer] ?: return
                emit(peer, flow, ReceiveEvent.FileCompleted(name = event.name))
                batch.receivedCount++
                if (batch.receivedCount >= batch.totalFiles) {
                    emit(
                        peer,
                        flow,
                        ReceiveEvent.BatchCompleted(received = batch.receivedCount, total = batch.totalFiles),
                    )
                    peerBatch.remove(peer)
                }
            }
            is InboundEvent.Failed -> {
                emit(peer, flow, ReceiveEvent.Failed(file = event.name, reason = event.reason))
            }
            is InboundEvent.ConnectionLost -> {
                val batch = peerBatch.remove(peer)
                val received = batch?.receivedCount ?: 0
                val total = (batch?.totalFiles ?: 0).coerceAtLeast(received)
                if (event.cancelled && batch != null) {
                    emit(peer, flow, ReceiveEvent.BatchCompleted(received = received, total = total))
                }
                emit(peer, flow, ReceiveEvent.ConnectionLost(receivedSoFar = received))
            }
            is InboundEvent.BatchCancelled -> {
                val batch = peerBatch[peer]
                if (batch == null || batch.batchId != event.batchId) return
                peerBatch.remove(peer)
                emit(
                    peer,
                    flow,
                    ReceiveEvent.BatchCompleted(
                        received = batch.receivedCount,
                        total = batch.totalFiles,
                        partialReason = PartialOutcome.SenderCancelled,
                    ),
                )
            }
        }
    }

    private fun ensureImplicitBatch(peer: PeerIdentity, flow: MutableSharedFlow<ReceiveEvent>): PeerBatch {
        val existing = peerBatch[peer]
        if (existing != null) return existing
        val implicit = PeerBatch(batchId = IMPLICIT_BATCH_ID, totalFiles = 1)
        peerBatch[peer] = implicit
        emit(peer, flow, ReceiveEvent.Started(currentFile = "", totalFiles = 1))
        return implicit
    }

    private companion object {
        const val IMPLICIT_BATCH_ID = "__implicit__"
    }
}

private data class PeerBatch(
    val batchId: String,
    val totalFiles: Int,
    var receivedCount: Int = 0,
)
