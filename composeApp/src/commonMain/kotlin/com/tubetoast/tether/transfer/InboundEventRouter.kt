@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.tubetoast.tether.transfer

import com.tubetoast.tether.protocol.PeerIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicReference

/**
 * Fans wire-level [InboundEvent]s out to per-peer [ReceiveEvent] flows, synthesizing the
 * batch-shaped events ([ReceiveEvent.Started], [ReceiveEvent.BatchCompleted]) the wire protocol
 * does not send explicitly: a sender that skips /batch-begin gets an implicit single-file batch,
 * and a batch completes either when the per-file count reaches the declared total or when the
 * sender signals it is done sending fewer files than declared.
 */
class InboundEventRouter(
    scope: CoroutineScope,
    inboundEvents: SharedFlow<InboundEvent>,
) {
    // Immutable snapshot updated via CAS so engines can register from any thread without
    // coordinating with the single collector coroutine that owns all batch-state mutation.
    private val peerFlows = AtomicReference(emptyMap<PeerIdentity, MutableSharedFlow<ReceiveEvent>>())
    private val peerBatch = mutableMapOf<PeerIdentity, PeerBatch>()

    private val _receiveEvents = MutableSharedFlow<Pair<PeerIdentity, ReceiveEvent>>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
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
            val newFlow = MutableSharedFlow<ReceiveEvent>(
                replay = 0,
                extraBufferCapacity = 64,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
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
                val received = peerBatch[peer]?.receivedCount ?: 0
                emit(peer, flow, ReceiveEvent.ConnectionLost(receivedSoFar = received))
            }
            is InboundEvent.CancelledByReceiver -> {
                val batch = peerBatch.remove(peer) ?: return
                emit(
                    peer,
                    flow,
                    ReceiveEvent.BatchCompleted(
                        received = batch.receivedCount,
                        total = batch.totalFiles,
                        partialReason = PartialOutcome.ReceiverCancelled,
                    ),
                )
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
            is InboundEvent.BatchEnd -> {
                val batch = peerBatch[peer] ?: return
                if (batch.batchId != event.batchId) return
                peerBatch.remove(peer)
                // A live batch at /batch-end means the count never reached the declared total:
                // the sender skipped files it could not read. Attribute the shortfall as such —
                // the receiver has no per-file signal for a source the sender never uploaded.
                val shortfall = batch.totalFiles - batch.receivedCount
                val partialReason = if (shortfall > 0) PartialOutcome.FilesUnreadable(shortfall) else null
                emit(
                    peer,
                    flow,
                    ReceiveEvent.BatchCompleted(
                        received = batch.receivedCount,
                        total = batch.totalFiles,
                        partialReason = partialReason,
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
