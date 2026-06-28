package com.tubetoast.tether.transfer

import com.tubetoast.tether.preferences.PeerPreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * Per-peer transfer state holder for the peer's session. Owns the live `PeerTransferState`
 * across multiple outbound batches (each driven through a freshly constructed `BatchSender`)
 * and inbound `ReceiveEvent`s. Holds cross-batch memory a single batch cannot — receipts the
 * peer has confirmed (filtered out on retry) and queued cancellations (consumed by the next
 * batch's skip predicate).
 */
class PeerTransferEngine(
    private val peer: PeerIdentity,
    private val batchSenderFactory: () -> BatchSender,
    private val inboundEvents: Flow<ReceiveEvent>,
    private val reconnectionTimeout: Duration = ReconnectionTimeout.DEFAULT,
    private val scope: CoroutineScope,
    private val peerPreferencesStore: PeerPreferencesStore,
) {
    private val _state = MutableStateFlow<PeerTransferState>(PeerTransferState.Idle)
    val state: StateFlow<PeerTransferState> = _state.asStateFlow()

    private var activeJob: Job? = null
    private var currentSender: BatchSender? = null
    private val originalSources = MutableStateFlow<List<FileSource>>(emptyList())
    private val confirmedReceived = MutableStateFlow<Set<String>>(emptySet())
    private val cancelledFileNames = MutableStateFlow<Set<String>>(emptySet())
    private val inboundReconnectCountdown = ReconnectCountdown(
        timeout = reconnectionTimeout,
        scope = scope,
        onTick = { remaining ->
            _state.update { s ->
                (s as? PeerTransferState.Reconnecting)?.copy(remainingSeconds = remaining) ?: s
            }
        },
        onExpired = {
            _state.update { s ->
                val perFile = (s as? PeerTransferState.Reconnecting)
                    ?.let { (it.snapshotBeforeDrop as? PeerTransferState.ActiveInbound)?.perFile }
                    ?: emptyList()
                PeerTransferState.Error(
                    reason = TransferErrorReason.NetworkLost,
                    sent = perFile.count { it is PerFileStatus.Done },
                    perFile = perFile,
                )
            }
        },
    )

    init {
        scope.launch {
            inboundEvents.collect { event -> handleInbound(event) }
        }
    }

    fun startOutbound(sources: List<FileSource>): Boolean {
        val current = _state.value
        if (current !is PeerTransferState.Idle) return false
        val claim = PeerTransferState.ActiveOutbound.Claimed(
            totalFiles = sources.size,
            totalBytes = sources.sumOf { it.sizeBytes ?: 0L },
            perFile = sources.map { PerFileStatus.Queued(it.name, it.sizeBytes) },
        )
        if (!_state.compareAndSet(current, claim)) return false
        originalSources.value = sources
        cancelledFileNames.update { emptySet() }
        launchBatch(sources)
        return true
    }

    fun onCancel() {
        activeJob?.cancel()
        activeJob = null
        _state.update { current ->
            if (current is PeerTransferState.ActiveOutbound) cancelledFromActive(current) else current
        }
    }

    fun onRetryOutbound() {
        val current = _state.value
        val lastPerFile = when (current) {
            is PeerTransferState.Sent -> current.perFile
            is PeerTransferState.Error -> current.perFile
            is PeerTransferState.Cancelled -> current.perFile
            else -> return
        }
        val failedNames = lastPerFile.filterIsInstance<PerFileStatus.Failed>().map { it.name }.toSet()
        val confirmed = confirmedReceived.value
        val retryable = originalSources.value.filter { it.name in failedNames && it.name !in confirmed }
        if (retryable.isEmpty()) return
        cancelledFileNames.update { emptySet() }
        launchBatch(retryable)
    }

    fun onRetryFile(name: String) {
        val source = originalSources.value.firstOrNull { it.name == name } ?: return
        cancelledFileNames.update { it - name }
        val job = activeJob
        activeJob = scope.launch {
            job?.cancel()
            job?.join()
            launchBatchIn(listOf(source))
        }
    }

    fun onCancelFile(name: String) {
        val current = _state.value as? PeerTransferState.ActiveOutbound ?: return
        val index = current.perFile.indexOfFirst { it.name == name }
        if (index < 0) return
        when (val status = current.perFile[index]) {
            is PerFileStatus.InProgress -> {
                val sender = currentSender
                if (sender != null) scope.launch { sender.cancelCurrent(name) }
            }
            is PerFileStatus.Queued -> {
                cancelledFileNames.update { it + name }
                _state.update { s ->
                    if (s !is PeerTransferState.ActiveOutbound) return@update s
                    val rowIndex = s.perFile.indexOfFirst { it.name == name }.takeIf { it >= 0 } ?: return@update s
                    val updated = s.perFile.toMutableList()
                    updated[rowIndex] = PerFileStatus.Failed(
                        status.name,
                        status.size,
                        FailureReason.CancelledByUser,
                        cancelledByUser = true,
                    )
                    val skipped = updated.count { it is PerFileStatus.Failed }
                    when (s) {
                        is PeerTransferState.ActiveOutbound.Sending -> s.copy(perFile = updated, skippedCount = skipped)
                        is PeerTransferState.ActiveOutbound.Preparing -> s.copy(
                            perFile = updated,
                            skippedCount = skipped,
                        )
                        is PeerTransferState.ActiveOutbound.Claimed -> s
                    }
                }
            }
            else -> Unit
        }
    }

    fun onDismiss() {
        _state.update { s ->
            if (s is PeerTransferState.Sent ||
                s is PeerTransferState.Received ||
                s is PeerTransferState.Cancelled ||
                s is PeerTransferState.Error
            ) {
                PeerTransferState.Idle
            } else {
                s
            }
        }
    }

    fun observeAutoSend(): Flow<Boolean> = peerPreferencesStore.observeAutoSend(peer)

    fun setAutoSend(enabled: Boolean) {
        scope.launch { peerPreferencesStore.setAutoSend(peer, enabled) }
    }

    private fun launchBatch(sources: List<FileSource>) {
        activeJob = scope.launch {
            launchBatchIn(sources)
        }
    }

    private suspend fun launchBatchIn(sources: List<FileSource>) {
        val sender = batchSenderFactory()
        currentSender = sender
        try {
            sender.run(sources, { source -> source.name in cancelledFileNames.value }) { progress ->
                _state.update { mapProgress(progress) }
            }
        } finally {
            currentSender = null
            _state.update { current ->
                if (current is PeerTransferState.ActiveOutbound) cancelledFromActive(current) else current
            }
        }
    }

    private fun cancelledFromActive(current: PeerTransferState.ActiveOutbound): PeerTransferState.Cancelled {
        val perFile = current.perFile.map { entry ->
            if (entry is PerFileStatus.Done) {
                entry
            } else {
                PerFileStatus.Failed(entry.name, entry.size, FailureReason.TransferCancelled)
            }
        }
        val sent = perFile.count { it is PerFileStatus.Done }
        val remaining = perFile.filter { it !is PerFileStatus.Done }.map { it.name }
        return PeerTransferState.Cancelled(sent = sent, remaining = remaining, perFile = perFile)
    }

    private fun mapProgress(progress: BatchProgress): PeerTransferState = when (progress) {
        is BatchProgress.Sending -> if (progress.preparing) {
            PeerTransferState.ActiveOutbound.Preparing(
                currentFile = progress.currentFile,
                currentIndex = progress.currentIndex,
                totalFiles = progress.totalFiles,
                sentBytes = progress.sentBytes,
                totalBytes = progress.totalBytes,
                skippedCount = progress.skippedCount,
                perFile = progress.perFile,
            )
        } else {
            PeerTransferState.ActiveOutbound.Sending(
                currentFile = progress.currentFile,
                currentIndex = progress.currentIndex,
                totalFiles = progress.totalFiles,
                sentBytes = progress.sentBytes,
                totalBytes = progress.totalBytes,
                bytesPerSec = progress.bytesPerSec,
                skippedCount = progress.skippedCount,
                perFile = progress.perFile,
            )
        }
        is BatchProgress.Reconnecting -> PeerTransferState.Reconnecting(
            direction = Direction.Outbound,
            remainingSeconds = progress.remainingSeconds,
            snapshotBeforeDrop = mapProgress(progress.snapshotBeforeDrop),
        )
        is BatchProgress.Completed -> mapCompleted(progress)
    }

    private fun mapCompleted(progress: BatchProgress.Completed): PeerTransferState {
        val perFile = progress.perFile
        return when (val outcome = progress.outcome) {
            is BatchOutcome.AllSent -> PeerTransferState.Sent(
                sent = perFile.count { it is PerFileStatus.Done },
                total = perFile.size,
                perFile = perFile,
                partialReason = null,
            )
            is BatchOutcome.PartialSent -> {
                val failedEntries = perFile.filterIsInstance<PerFileStatus.Failed>()
                val doneCount = perFile.count { it is PerFileStatus.Done }
                PeerTransferState.Sent(
                    sent = doneCount,
                    total = perFile.size,
                    perFile = perFile,
                    partialReason = failedEntries.dominantPartialReason(),
                )
            }
            is BatchOutcome.Cancelled -> PeerTransferState.Cancelled(
                sent = outcome.sent,
                remaining = outcome.remaining,
                perFile = perFile,
            )
            is BatchOutcome.Failed -> PeerTransferState.Error(
                reason = outcome.reason,
                sent = outcome.sent,
                perFile = perFile,
            )
        }
    }

    private fun handleInbound(event: ReceiveEvent) {
        when (event) {
            is ReceiveEvent.Started -> {
                inboundReconnectCountdown.cancel()
                val perFile: List<PerFileStatus> = List(event.totalFiles) { i ->
                    if (i == 0) PerFileStatus.Queued(event.currentFile, null) else PerFileStatus.Queued("", null)
                }
                _state.update {
                    PeerTransferState.ActiveInbound(
                        currentFile = event.currentFile,
                        currentIndex = 0,
                        totalFiles = event.totalFiles,
                        receivedBytes = 0L,
                        totalBytes = null,
                        bytesPerSec = null,
                        perFile = perFile,
                    )
                }
            }
            is ReceiveEvent.Progress -> {
                inboundReconnectCountdown.cancel()
                _state.update { s ->
                    val active = s as? PeerTransferState.ActiveInbound ?: return@update s
                    val index = active.perFile.indexOfFirst { it.name == event.name }.takeIf { it >= 0 }
                        ?: active.perFile.indexOfFirst { it.name.isEmpty() }.takeIf { it >= 0 }
                        ?: active.currentIndex
                    val updated = active.perFile.toMutableList()
                    updated[index] = PerFileStatus.InProgress(event.name, event.totalBytes, event.receivedBytes)
                    active.copy(
                        currentFile = event.name,
                        receivedBytes = event.receivedBytes,
                        totalBytes = event.totalBytes,
                        perFile = updated,
                    )
                }
            }
            is ReceiveEvent.FileCompleted -> {
                inboundReconnectCountdown.cancel()
                confirmedReceived.update { it + event.name }
                _state.update { s ->
                    val active = s as? PeerTransferState.ActiveInbound ?: return@update s
                    val index = active.perFile.indexOfFirst { it.name == event.name }.takeIf { it >= 0 }
                        ?: active.currentIndex
                    val updated = active.perFile.toMutableList()
                    updated[index] = PerFileStatus.Done(event.name, active.perFile[index].size)
                    active.copy(
                        currentIndex = (index + 1).coerceAtMost(active.totalFiles - 1),
                        perFile = updated,
                    )
                }
            }
            is ReceiveEvent.BatchCompleted -> {
                inboundReconnectCountdown.cancel()
                _state.update { s ->
                    val perFile = (s as? PeerTransferState.ActiveInbound)?.perFile ?: emptyList()
                    val partialReason = if (event.received < event.total) {
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
                        received = event.received,
                        total = event.total,
                        perFile = perFile,
                        partialReason = partialReason,
                    )
                }
            }
            is ReceiveEvent.Failed -> {
                _state.update { s ->
                    val active = s as? PeerTransferState.ActiveInbound ?: return@update s
                    val index = active.perFile.indexOfFirst { it.name == event.file }.takeIf { it >= 0 }
                        ?: active.currentIndex
                    val updated = active.perFile.toMutableList()
                    updated[index] = PerFileStatus.Failed(event.file, active.perFile[index].size, event.reason)
                    active.copy(perFile = updated)
                }
            }
            is ReceiveEvent.ConnectionLost -> {
                _state.update { snapshot ->
                    PeerTransferState.Reconnecting(
                        direction = Direction.Inbound,
                        remainingSeconds = reconnectionTimeout.inWholeSeconds.toInt(),
                        snapshotBeforeDrop = snapshot,
                    )
                }
                inboundReconnectCountdown.start()
            }
            ReceiveEvent.ReceiverSuspended -> {
                inboundReconnectCountdown.cancel()
                _state.update { s ->
                    val perFile = (s as? PeerTransferState.ActiveInbound)?.perFile ?: emptyList()
                    val doneCount = perFile.count { it is PerFileStatus.Done }
                    PeerTransferState.Error(
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
