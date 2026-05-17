package com.tubetoast.tether.presentation.transfer

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackCallback
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.FileSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class TransferComponent(
    componentContext: ComponentContext,
    val peer: Device,
    private val sources: List<FileSource>,
    private val batchSender: BatchSender,
    private val onExit: () -> Unit,
    private val scope: CoroutineScope = componentContext.coroutineScope(),
) : ComponentContext by componentContext {
    private val _state = MutableValue<TransferState>(TransferState.Preparing)
    val state: Value<TransferState> = _state

    private var sendJob: Job? = null
    private var remainingSources: List<FileSource> = sources

    init {
        val needsConfirmation = exceedsThreshold(sources)
        if (needsConfirmation) {
            val totalBytes = sources.mapNotNull { it.size }.sum()
            _state.value = TransferState.FolderConfirm(sources.size, totalBytes)
        } else {
            startBatch(sources)
        }
        backHandler.register(BackCallback { onBackPressed() })
    }

    fun onFolderConfirm(continue_: Boolean) {
        if (!continue_) {
            onExit()
            return
        }
        startBatch(sources)
    }

    fun onCancelClicked() {
        val current = _state.value
        when (current) {
            is TransferState.InProgress -> _state.value = TransferState.CancelConfirm(current)
            is TransferState.Preparing -> {
                val placeholder = TransferState.InProgress(
                    currentFile = "",
                    bytesDone = 0L,
                    bytesTotal = null,
                    speedBytesPerSec = 0L,
                )
                _state.value = TransferState.CancelConfirm(placeholder)
            }
            else -> {}
        }
    }

    fun onCancelConfirmed() {
        sendJob?.cancel()
        _state.value = TransferState.Terminal.Cancelled
    }

    fun onKeepSending() {
        val current = _state.value
        if (current is TransferState.CancelConfirm) {
            _state.value = current.snapshot
        }
    }

    fun onRetryAll() {
        startBatch(remainingSources)
    }

    fun onRetryFile(name: String) {
        val source = sources.firstOrNull { it.name == name } ?: return
        startBatch(listOf(source))
    }

    fun onDone() {
        onExit()
    }

    fun onBackPressed() {
        when (_state.value) {
            is TransferState.InProgress, is TransferState.Preparing -> onCancelClicked()
            is TransferState.Terminal -> onExit()
            else -> onExit()
        }
    }

    private fun startBatch(batch: List<FileSource>) {
        remainingSources = batch
        _state.value = TransferState.Preparing
        sendJob?.cancel()
        sendJob = scope.launch {
            val outcome = batchSender.run(peer, batch) { newState ->
                _state.value = newState
            }
            _state.value = buildTerminal(outcome)
        }
    }

    private fun buildTerminal(outcome: BatchOutcome): TransferState.Terminal = when {
        outcome.connectionLostMidway -> TransferState.Terminal.ConnectionErrorSummary(
            peer = peer,
            sent = outcome.sent,
            total = outcome.total,
            failed = outcome.failed,
        )

        outcome.failed.isEmpty() -> TransferState.Terminal.AllSuccess(peer, outcome.sent)

        outcome.sent == 0 -> TransferState.Terminal.AllFailed(peer, outcome.failed)

        else -> TransferState.Terminal.PartialFailure(
            peer = peer,
            sent = outcome.sent,
            total = outcome.total,
            failed = outcome.failed,
        )
    }
}
