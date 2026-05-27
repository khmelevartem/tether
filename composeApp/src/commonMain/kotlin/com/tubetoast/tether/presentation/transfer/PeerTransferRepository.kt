package com.tubetoast.tether.presentation.transfer

import com.tubetoast.tether.transfer.BatchOutcome
import com.tubetoast.tether.transfer.BatchProgress
import com.tubetoast.tether.transfer.BatchSender
import com.tubetoast.tether.transfer.FailureReason
import com.tubetoast.tether.transfer.FileSource
import com.tubetoast.tether.transfer.PartialOutcome
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.transfer.PerFileStatus
import com.tubetoast.tether.transfer.ReceiveEvent
import com.tubetoast.tether.transfer.ReconnectionTimeout
import com.tubetoast.tether.transfer.TransferErrorReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration

interface PeerTransferRepository {
    fun observe(peer: PeerIdentity): StateFlow<PeerTransferState>

    fun startOutbound(peer: PeerIdentity, sources: List<FileSource>)

    fun cancel(peer: PeerIdentity)

    fun retry(peer: PeerIdentity)

    fun retryFile(peer: PeerIdentity, name: String)

    fun cancelFile(peer: PeerIdentity, name: String)

    fun dismiss(peer: PeerIdentity)

    fun toggleExpanded(peer: PeerIdentity)
}

class PeerTransferRepositoryImpl(
    private val batchSenderFactory: () -> BatchSender,
    private val inboundEvents: Flow<ReceiveEvent>,
    private val scope: CoroutineScope,
    private val reconnectionTimeout: Duration = ReconnectionTimeout.DEFAULT,
) : PeerTransferRepository {
    private val states = mutableMapOf<PeerIdentity, MutableStateFlow<PeerTransferState>>()
    private val activeJobs = mutableMapOf<PeerIdentity, Job?>()
    private val currentSenders = mutableMapOf<PeerIdentity, BatchSender?>()
    private val originalSources = mutableMapOf<PeerIdentity, List<FileSource>>()
    private val confirmedReceived = mutableMapOf<PeerIdentity, MutableStateFlow<Set<String>>>()
    private val cancelledFileNames = mutableMapOf<PeerIdentity, MutableStateFlow<Set<String>>>()

    init {
        scope.launch {
            inboundEvents.collect { ev -> handleInbound(ev) }
        }
    }

    override fun observe(peer: PeerIdentity): StateFlow<PeerTransferState> =
        stateFor(peer).asStateFlow()

    override fun startOutbound(peer: PeerIdentity, sources: List<FileSource>) {
        val current = stateFor(peer).value
        if (current is PeerTransferState.ActiveOutbound ||
            current is PeerTransferState.ActiveInbound ||
            current is PeerTransferState.Reconnecting
        ) {
            return
        }
        originalSources[peer] = sources
        cancelledFor(peer).value = emptySet()
        launchBatch(peer, sources)
    }

    override fun cancel(peer: PeerIdentity) {
        activeJobs[peer]?.cancel()
        activeJobs[peer] = null
    }

    override fun retry(peer: PeerIdentity) {
        val current = stateFor(peer).value
        val lastPerFile = when (current) {
            is PeerTransferState.Sent -> current.perFile
            is PeerTransferState.Error -> current.perFile
            is PeerTransferState.Cancelled -> current.perFile
            else -> return
        }
        val failedNames = lastPerFile.filterIsInstance<PerFileStatus.Failed>().map { it.name }.toSet()
        val confirmed = confirmedFor(peer).value
        val retryable = (originalSources[peer] ?: return).filter { it.name in failedNames && it.name !in confirmed }
        if (retryable.isEmpty()) return
        cancelledFor(peer).value = emptySet()
        launchBatch(peer, retryable)
    }

    override fun retryFile(peer: PeerIdentity, name: String) {
        val source = (originalSources[peer] ?: return).firstOrNull { it.name == name } ?: return
        cancelledFor(peer).update { it - name }
        val existingJob = activeJobs[peer]
        activeJobs[peer] = scope.launch {
            existingJob?.cancel()
            existingJob?.join()
            launchBatchIn(peer, listOf(source))
        }
    }

    override fun cancelFile(peer: PeerIdentity, name: String) {
        val state = stateFor(peer)
        val current = state.value as? PeerTransferState.ActiveOutbound ?: return
        val idx = current.perFile.indexOfFirst { it.name == name }
        if (idx < 0) return
        when (val status = current.perFile[idx]) {
            is PerFileStatus.InProgress -> {
                val sender = currentSenders[peer]
                if (sender != null) scope.launch { sender.cancelCurrent(name) }
            }
            is PerFileStatus.Queued -> {
                cancelledFor(peer).update { it + name }
                state.update { s ->
                    val active = s as? PeerTransferState.ActiveOutbound ?: return@update s
                    val rowIdx = active.perFile.indexOfFirst { it.name == name }.takeIf { it >= 0 }
                        ?: return@update s
                    val updated = active.perFile.toMutableList()
                    updated[rowIdx] = PerFileStatus.Failed(
                        status.name,
                        status.size,
                        FailureReason.CancelledByUser,
                        cancelledByUser = true,
                    )
                    active.copy(
                        perFile = updated,
                        skippedCount = updated.count { it is PerFileStatus.Failed },
                    )
                }
            }
            else -> Unit
        }
    }

    override fun dismiss(peer: PeerIdentity) {
        stateFor(peer).update { s ->
            if (s is PeerTransferState.Sent ||
                s is PeerTransferState.Received ||
                s is PeerTransferState.Cancelled ||
                s is PeerTransferState.Error
            ) {
                PeerTransferState.Idle(peer)
            } else {
                s
            }
        }
    }

    override fun toggleExpanded(peer: PeerIdentity) {
        stateFor(peer).update { s ->
            (s as? PeerTransferState.Idle)?.copy(expanded = !s.expanded) ?: s
        }
    }

    private fun stateFor(peer: PeerIdentity): MutableStateFlow<PeerTransferState> =
        states.getOrPut(peer) { MutableStateFlow(PeerTransferState.Idle(peer)) }

    private fun confirmedFor(peer: PeerIdentity): MutableStateFlow<Set<String>> =
        confirmedReceived.getOrPut(peer) { MutableStateFlow(emptySet()) }

    private fun cancelledFor(peer: PeerIdentity): MutableStateFlow<Set<String>> =
        cancelledFileNames.getOrPut(peer) { MutableStateFlow(emptySet()) }

    private fun launchBatch(peer: PeerIdentity, sources: List<FileSource>) {
        activeJobs[peer] = scope.launch {
            launchBatchIn(peer, sources)
        }
    }

    private suspend fun launchBatchIn(peer: PeerIdentity, sources: List<FileSource>) {
        val sender = batchSenderFactory()
        currentSenders[peer] = sender
        try {
            sender.run(sources, peer, { src -> src.name in cancelledFor(peer).value }) { progress ->
                stateFor(peer).value = mapProgress(peer, progress)
            }
        } finally {
            currentSenders[peer] = null
        }
    }

    private fun mapProgress(peer: PeerIdentity, progress: BatchProgress): PeerTransferState =
        when (progress) {
            is BatchProgress.Sending -> PeerTransferState.ActiveOutbound(
                peer = progress.peer,
                currentFile = progress.currentFile,
                currentIndex = progress.currentIndex,
                totalFiles = progress.totalFiles,
                sentBytes = progress.sentBytes,
                totalBytes = progress.totalBytes,
                bytesPerSec = progress.bytesPerSec,
                skippedCount = progress.skippedCount,
                perFile = progress.perFile,
            )
            is BatchProgress.Reconnecting -> PeerTransferState.Reconnecting(
                peer = progress.peer,
                direction = Direction.Outbound,
                remainingSeconds = progress.remainingSeconds,
                snapshotBeforeDrop = mapProgress(peer, progress.snapshotBeforeDrop),
            )
            is BatchProgress.Completed -> mapCompleted(progress)
        }

    private fun mapCompleted(progress: BatchProgress.Completed): PeerTransferState {
        val peer = progress.peer
        val perFile = progress.perFile
        return when (val outcome = progress.outcome) {
            is BatchOutcome.AllSent -> PeerTransferState.Sent(
                peer = peer,
                sent = perFile.count { it is PerFileStatus.Done },
                total = perFile.size,
                perFile = perFile,
                partialReason = null,
            )
            is BatchOutcome.PartialSent -> {
                val failedEntries = perFile.filterIsInstance<PerFileStatus.Failed>()
                val doneCount = perFile.count { it is PerFileStatus.Done }
                PeerTransferState.Sent(
                    peer = peer,
                    sent = doneCount,
                    total = perFile.size,
                    perFile = perFile,
                    partialReason = failedEntries.dominantPartialReason(),
                )
            }
            is BatchOutcome.Cancelled -> PeerTransferState.Cancelled(
                peer = peer,
                sent = outcome.sent,
                remaining = outcome.remaining,
                perFile = perFile,
            )
            is BatchOutcome.Failed -> PeerTransferState.Error(
                peer = peer,
                reason = outcome.reason,
                sent = outcome.sent,
                perFile = perFile,
            )
        }
    }

    private fun handleInbound(ev: ReceiveEvent) {
        // Inbound events carry no PeerIdentity — route to whichever peer is in an inbound state.
        // TODO(#191): route per-peer once the server exposes the sender identity.
        val inboundPeer = states.entries
            .firstOrNull { it.value.value is PeerTransferState.ActiveInbound }
            ?.key
            ?: states.entries
                .firstOrNull {
                    val s = it.value.value
                    s is PeerTransferState.Reconnecting && s.direction == Direction.Inbound
                }?.key
            ?: states.entries.firstOrNull()?.key
            ?: return

        val state = stateFor(inboundPeer)
        when (ev) {
            is ReceiveEvent.Started -> {
                val perFile: List<PerFileStatus> = List(ev.totalFiles) { i ->
                    if (i == 0) PerFileStatus.Queued(ev.currentFile, null) else PerFileStatus.Queued("", null)
                }
                state.value = PeerTransferState.ActiveInbound(
                    peer = inboundPeer,
                    currentFile = ev.currentFile,
                    currentIndex = 0,
                    totalFiles = ev.totalFiles,
                    receivedBytes = 0L,
                    totalBytes = null,
                    bytesPerSec = null,
                    perFile = perFile,
                )
            }
            is ReceiveEvent.Progress -> {
                state.update { s ->
                    val active = s as? PeerTransferState.ActiveInbound ?: return@update s
                    val idx = active.perFile.indexOfFirst { it.name == ev.name }.takeIf { it >= 0 }
                        ?: active.perFile.indexOfFirst { it.name.isEmpty() }.takeIf { it >= 0 }
                        ?: active.currentIndex
                    val updated = active.perFile.toMutableList()
                    updated[idx] = PerFileStatus.InProgress(ev.name, ev.totalBytes, ev.receivedBytes)
                    active.copy(
                        currentFile = ev.name,
                        receivedBytes = ev.receivedBytes,
                        totalBytes = ev.totalBytes,
                        perFile = updated,
                    )
                }
            }
            is ReceiveEvent.FileCompleted -> {
                confirmedFor(inboundPeer).update { it + ev.name }
                state.update { s ->
                    val active = s as? PeerTransferState.ActiveInbound ?: return@update s
                    val idx = active.perFile.indexOfFirst { it.name == ev.name }.takeIf { it >= 0 }
                        ?: active.currentIndex
                    val updated = active.perFile.toMutableList()
                    updated[idx] = PerFileStatus.Done(ev.name, active.perFile[idx].size)
                    active.copy(
                        currentIndex = (idx + 1).coerceAtMost(active.totalFiles - 1),
                        perFile = updated,
                    )
                }
            }
            is ReceiveEvent.BatchCompleted -> {
                state.update { s ->
                    val perFile = (s as? PeerTransferState.ActiveInbound)?.perFile ?: emptyList()
                    val partialReason = if (ev.received < ev.total) {
                        val failedEntries = perFile.filterIsInstance<PerFileStatus.Failed>()
                        val cancelledCount = failedEntries.count { it.reason is FailureReason.CancelledByUser }
                        if (cancelledCount > 0 && cancelledCount == failedEntries.size) {
                            PartialOutcome.ReceiverCancelled
                        } else {
                            PartialOutcome.ConnectionLost
                        }
                    } else {
                        null
                    }
                    PeerTransferState.Received(
                        peer = inboundPeer,
                        received = ev.received,
                        total = ev.total,
                        perFile = perFile,
                        partialReason = partialReason,
                    )
                }
            }
            is ReceiveEvent.Failed -> {
                state.update { s ->
                    val active = s as? PeerTransferState.ActiveInbound ?: return@update s
                    val idx = active.perFile.indexOfFirst { it.name == ev.file }.takeIf { it >= 0 }
                        ?: active.currentIndex
                    val updated = active.perFile.toMutableList()
                    updated[idx] = PerFileStatus.Failed(ev.file, active.perFile[idx].size, ev.reason)
                    active.copy(perFile = updated)
                }
            }
            is ReceiveEvent.ConnectionLost -> {
                state.update { snapshot ->
                    PeerTransferState.Reconnecting(
                        peer = inboundPeer,
                        direction = Direction.Inbound,
                        remainingSeconds = reconnectionTimeout.inWholeSeconds.toInt(),
                        snapshotBeforeDrop = snapshot,
                    )
                }
            }
            ReceiveEvent.ReceiverSuspended -> {
                state.update { s ->
                    val perFile = (s as? PeerTransferState.ActiveInbound)?.perFile ?: emptyList()
                    val doneCount = perFile.count { it is PerFileStatus.Done }
                    PeerTransferState.Error(
                        peer = inboundPeer,
                        reason = TransferErrorReason.ReceiverSuspended,
                        sent = doneCount,
                        perFile = perFile,
                    )
                }
            }
        }
    }
}

private fun List<PerFileStatus.Failed>.dominantPartialReason(): PartialOutcome {
    val unreadableCount = count { it.reason is FailureReason.Unreadable }
    val cancelledByUserCount = count { it.cancelledByUser }
    return when {
        unreadableCount == size -> PartialOutcome.FilesUnreadable(unreadableCount)
        cancelledByUserCount == size -> PartialOutcome.SenderCancelled
        all { it.reason is FailureReason.PeerUnreachable } -> PartialOutcome.PeerUnreachable
        all { it.reason is FailureReason.ReceiverWriteFailed } -> PartialOutcome.ReceiverWriteFailed
        else -> PartialOutcome.ConnectionLost
    }
}
