package com.tubetoast.tether.presentation.transfer

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.tubetoast.tether.transfer.ConnectionMonitor
import com.tubetoast.tether.transfer.FailureReason
import com.tubetoast.tether.transfer.FileSource
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.transfer.ReceiveEvent
import com.tubetoast.tether.transfer.ReconnectionTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration

class PeerTransferComponent(
    componentContext: ComponentContext,
    val peer: PeerIdentity,
    private val batchSenderFactory: () -> BatchSender,
    private val inboundEvents: Flow<ReceiveEvent>,
    private val onShowDetailsCallback: (PeerIdentity) -> Unit,
    private val connectionMonitor: ConnectionMonitor,
    private val reconnectionTimeout: Duration = ReconnectionTimeout.DEFAULT,
    private val scope: CoroutineScope,
) : ComponentContext by componentContext {
    private val mutableState = MutableValue<PeerTransferState>(PeerTransferState.Idle(peer))
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
                val updated = current.perFile.toMutableList()
                updated[idx] = PerFileStatus.Failed(
                    status.name,
                    status.size,
                    FailureReason.CancelledByUser,
                    cancelledByUser = true,
                )
                mutableState.value = current.copy(
                    perFile = updated,
                    skippedCount = updated.count { it is PerFileStatus.Failed },
                )
            }
            else -> Unit
        }
    }

    fun onDismiss() {
        val current = mutableState.value
        if (current is PeerTransferState.Sent ||
            current is PeerTransferState.Received ||
            current is PeerTransferState.Cancelled ||
            current is PeerTransferState.Error
        ) {
            mutableState.value = PeerTransferState.Idle(peer)
        }
    }

    fun toggleExpanded() {
        val current = mutableState.value
        if (current is PeerTransferState.Idle) {
            mutableState.value = current.copy(expanded = !current.expanded)
        }
    }

    fun onShowDetails() = onShowDetailsCallback(peer)

    private fun launchBatch(sources: List<FileSource>) {
        activeJob = scope.launch {
            launchBatchIn(sources)
        }
    }

    private suspend fun launchBatchIn(sources: List<FileSource>) {
        val sender = batchSenderFactory()
        currentSender = sender
        try {
            sender.run(sources, peer, { src -> src.name in cancelledFileNames.value }) { st -> mutableState.value = st }
        } finally {
            currentSender = null
        }
    }

    private fun handleInbound(ev: ReceiveEvent) {
        when (ev) {
            is ReceiveEvent.Started -> {
                val perFile: List<PerFileStatus> = List(ev.totalFiles) { i ->
                    if (i == 0) PerFileStatus.Queued(ev.currentFile, null) else PerFileStatus.Queued("", null)
                }
                mutableState.value = PeerTransferState.ActiveInbound(
                    peer = peer,
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
                val current = mutableState.value as? PeerTransferState.ActiveInbound ?: return
                val idx = current.perFile.indexOfFirst { it.name == ev.name }.takeIf { it >= 0 }
                    ?: current.perFile.indexOfFirst { it.name.isEmpty() }.takeIf { it >= 0 }
                    ?: current.currentIndex
                val updated = current.perFile.toMutableList()
                updated[idx] = PerFileStatus.InProgress(ev.name, ev.totalBytes, ev.receivedBytes)
                mutableState.value = current.copy(
                    currentFile = ev.name,
                    receivedBytes = ev.receivedBytes,
                    totalBytes = ev.totalBytes,
                    perFile = updated,
                )
            }
            is ReceiveEvent.FileCompleted -> {
                confirmedReceived.update { it + ev.name }
                val current = mutableState.value as? PeerTransferState.ActiveInbound ?: return
                val idx = current.perFile.indexOfFirst { it.name == ev.name }.takeIf { it >= 0 }
                    ?: current.currentIndex
                val updated = current.perFile.toMutableList()
                updated[idx] = PerFileStatus.Done(ev.name, current.perFile[idx].size)
                mutableState.value = current.copy(
                    currentIndex = (idx + 1).coerceAtMost(current.totalFiles - 1),
                    perFile = updated,
                )
            }
            is ReceiveEvent.BatchCompleted -> {
                val current = mutableState.value
                val perFile = (current as? PeerTransferState.ActiveInbound)?.perFile ?: emptyList()
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
                mutableState.value = PeerTransferState.Received(
                    peer = peer,
                    received = ev.received,
                    total = ev.total,
                    perFile = perFile,
                    partialReason = partialReason,
                )
            }
            is ReceiveEvent.Failed -> {
                val current = mutableState.value as? PeerTransferState.ActiveInbound ?: return
                val idx = current.perFile.indexOfFirst { it.name == ev.file }.takeIf { it >= 0 }
                    ?: current.currentIndex
                val updated = current.perFile.toMutableList()
                updated[idx] = PerFileStatus.Failed(ev.file, current.perFile[idx].size, ev.reason)
                mutableState.value = current.copy(perFile = updated)
            }
            is ReceiveEvent.ConnectionLost -> {
                val snapshot = mutableState.value
                mutableState.value = PeerTransferState.Reconnecting(
                    peer = peer,
                    direction = Direction.Inbound,
                    remainingSeconds = reconnectionTimeout.inWholeSeconds.toInt(),
                    snapshotBeforeDrop = snapshot,
                )
            }
            ReceiveEvent.ReceiverSuspended -> {
                val current = mutableState.value
                val perFile = (current as? PeerTransferState.ActiveInbound)?.perFile ?: emptyList()
                val doneCount = perFile.count { it is PerFileStatus.Done }
                mutableState.value = PeerTransferState.Error(
                    peer = peer,
                    reason = TransferErrorReason.ReceiverSuspended,
                    sent = doneCount,
                    perFile = perFile,
                )
            }
        }
    }
}
