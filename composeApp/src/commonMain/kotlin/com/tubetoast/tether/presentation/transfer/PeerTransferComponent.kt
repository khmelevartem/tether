package com.tubetoast.tether.presentation.transfer

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.tubetoast.tether.preferences.PeerPreferencesStore
import com.tubetoast.tether.presentation.peer.Peer
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration

class PeerTransferComponent(
    componentContext: ComponentContext,
    val peer: Peer,
    private val lifecycleRegistry: LifecycleRegistry,
    private val batchSenderFactory: () -> BatchSender,
    // TODO(#195): wire real ReceiveEvent source when Receiver UI lands
    private val inboundEvents: Flow<ReceiveEvent> = MutableSharedFlow(),
    onShowDetails: (PeerIdentity) -> Unit,
    private val reconnectionTimeout: Duration = ReconnectionTimeout.DEFAULT,
    private val scope: CoroutineScope,
    private val pendingFilesRepository: PendingFilesRepository? = null,
    private val peerPreferencesStore: PeerPreferencesStore? = null,
    // TODO(#192/#193/#194): platform actuals wire real file picker here
    private val onOpenPicker: () -> Unit = {},
) : ComponentContext by componentContext {
    fun destroyContext() {
        lifecycleRegistry.destroy()
    }

    private val showDetailsCallback = onShowDetails
    private val mutableState = MutableValue<PeerTransferState>(PeerTransferState.Idle(peer.id))
    val state: Value<PeerTransferState> = mutableState
    private var activeJob: Job? = null
    private var currentSender: BatchSender? = null
    private var originalSources: List<FileSource> = emptyList()
    private val confirmedReceived = MutableStateFlow<Set<String>>(emptySet())
    private val cancelledFileNames = MutableStateFlow<Set<String>>(emptySet())

    init {
        scope.launch {
            inboundEvents.collect { ev -> handleInbound(ev) }
        }
    }

    fun startOutbound(sources: List<FileSource>) {
        val current = mutableState.value
        if (current is PeerTransferState.ActiveOutbound ||
            current is PeerTransferState.ActiveInbound ||
            current is PeerTransferState.Reconnecting
        ) {
            return
        }
        originalSources = sources
        cancelledFileNames.value = emptySet()
        launchBatch(sources)
    }

    fun onCardClick() {
        val sources = pendingFilesRepository?.sources?.value.orEmpty()
        if (sources.isNotEmpty()) {
            startOutbound(sources)
            pendingFilesRepository?.clear()
        } else {
            onOpenPicker()
        }
    }

    fun observeAutoSend(): Flow<Boolean> =
        peerPreferencesStore?.observeAutoSend(peer.id) ?: flowOf(false)

    fun setAutoSend(enabled: Boolean) {
        val store = peerPreferencesStore ?: return
        scope.launch { store.setAutoSend(peer.id, enabled) }
    }

    fun onCancel() {
        activeJob?.cancel()
        activeJob = null
    }

    fun onRetry() {
        val current = mutableState.value
        val lastPerFile = when (current) {
            is PeerTransferState.Sent -> current.perFile
            is PeerTransferState.Error -> current.perFile
            is PeerTransferState.Cancelled -> current.perFile
            else -> return
        }
        val failedNames = lastPerFile.filterIsInstance<PerFileStatus.Failed>().map { it.name }.toSet()
        val confirmed = confirmedReceived.value
        val retryable = originalSources.filter { it.name in failedNames && it.name !in confirmed }
        if (retryable.isEmpty()) return
        cancelledFileNames.value = emptySet()
        launchBatch(retryable)
    }

    fun onRetryFile(name: String) {
        val source = originalSources.firstOrNull { it.name == name } ?: return
        cancelledFileNames.update { it - name }
        val job = activeJob
        activeJob = scope.launch {
            job?.cancel()
            job?.join()
            launchBatchIn(listOf(source))
        }
    }

    fun onCancelFile(name: String) {
        val current = mutableState.value as? PeerTransferState.ActiveOutbound ?: return
        val idx = current.perFile.indexOfFirst { it.name == name }
        if (idx < 0) return
        when (val status = current.perFile[idx]) {
            is PerFileStatus.InProgress -> {
                val sender = currentSender
                if (sender != null) scope.launch { sender.cancelCurrent(name) }
            }
            is PerFileStatus.Queued -> {
                cancelledFileNames.update { it + name }
                mutableState.update { s ->
                    val active = s as? PeerTransferState.ActiveOutbound ?: return@update s
                    val rowIdx = active.perFile.indexOfFirst { it.name == name }.takeIf { it >= 0 } ?: return@update s
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

    fun onDismiss() {
        mutableState.update { s ->
            if (s is PeerTransferState.Sent ||
                s is PeerTransferState.Received ||
                s is PeerTransferState.Cancelled ||
                s is PeerTransferState.Error
            ) {
                PeerTransferState.Idle(peer.id)
            } else {
                s
            }
        }
    }

    fun toggleExpanded() {
        mutableState.update { s ->
            (s as? PeerTransferState.Idle)?.copy(expanded = !s.expanded) ?: s
        }
    }

    fun onShowDetails() = showDetailsCallback(peer.id)

    private fun launchBatch(sources: List<FileSource>) {
        activeJob = scope.launch {
            launchBatchIn(sources)
        }
    }

    private suspend fun launchBatchIn(sources: List<FileSource>) {
        val sender = batchSenderFactory()
        currentSender = sender
        try {
            sender.run(sources, peer.id, { src -> src.name in cancelledFileNames.value }) { progress ->
                mutableState.value = mapProgress(progress)
            }
        } finally {
            currentSender = null
        }
    }

    private fun mapProgress(progress: BatchProgress): PeerTransferState = when (progress) {
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
            snapshotBeforeDrop = mapProgress(progress.snapshotBeforeDrop),
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
        when (ev) {
            is ReceiveEvent.Started -> {
                val perFile: List<PerFileStatus> = List(ev.totalFiles) { i ->
                    if (i == 0) PerFileStatus.Queued(ev.currentFile, null) else PerFileStatus.Queued("", null)
                }
                mutableState.value = PeerTransferState.ActiveInbound(
                    peer = peer.id,
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
                mutableState.update { s ->
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
                confirmedReceived.update { it + ev.name }
                mutableState.update { s ->
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
                mutableState.update { s ->
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
                        peer = peer.id,
                        received = ev.received,
                        total = ev.total,
                        perFile = perFile,
                        partialReason = partialReason,
                    )
                }
            }
            is ReceiveEvent.Failed -> {
                mutableState.update { s ->
                    val active = s as? PeerTransferState.ActiveInbound ?: return@update s
                    val idx = active.perFile.indexOfFirst { it.name == ev.file }.takeIf { it >= 0 }
                        ?: active.currentIndex
                    val updated = active.perFile.toMutableList()
                    updated[idx] = PerFileStatus.Failed(ev.file, active.perFile[idx].size, ev.reason)
                    active.copy(perFile = updated)
                }
            }
            is ReceiveEvent.ConnectionLost -> {
                mutableState.update { snapshot ->
                    PeerTransferState.Reconnecting(
                        peer = peer.id,
                        direction = Direction.Inbound,
                        remainingSeconds = reconnectionTimeout.inWholeSeconds.toInt(),
                        snapshotBeforeDrop = snapshot,
                    )
                }
            }
            ReceiveEvent.ReceiverSuspended -> {
                mutableState.update { s ->
                    val perFile = (s as? PeerTransferState.ActiveInbound)?.perFile ?: emptyList()
                    val doneCount = perFile.count { it is PerFileStatus.Done }
                    PeerTransferState.Error(
                        peer = peer.id,
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
