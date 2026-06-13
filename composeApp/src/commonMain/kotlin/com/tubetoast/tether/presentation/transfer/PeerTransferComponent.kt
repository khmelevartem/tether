package com.tubetoast.tether.presentation.transfer

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.tubetoast.tether.peer.Peer
import com.tubetoast.tether.preferences.FileTransferPreferences
import com.tubetoast.tether.presentation.banners.PeerConflictRelay
import com.tubetoast.tether.protocol.DeviceType
import com.tubetoast.tether.transfer.FilePicker
import com.tubetoast.tether.transfer.FileSource
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.transfer.PeerTransferEngine
import com.tubetoast.tether.transfer.PeerTransferState
import com.tubetoast.tether.transfer.Pending
import com.tubetoast.tether.transfer.PendingFilesRepository
import com.tubetoast.tether.transfer.PendingFilesSummary
import com.tubetoast.tether.transfer.PickKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Sources staged for send, pending user confirmation. */
data class PendingLargeConfirm(
    val sources: List<FileSource>,
    val summary: PendingFilesSummary,
    val dontShowAgain: Boolean = false,
)

class PeerTransferComponent(
    componentContext: ComponentContext,
    peer: Peer,
    private val lifecycleRegistry: LifecycleRegistry,
    private val engine: PeerTransferEngine,
    onShowDetails: (PeerIdentity) -> Unit,
    private val scope: CoroutineScope,
    private val pendingFilesRepository: PendingFilesRepository,
    private val filePicker: FilePicker,
    private val conflictRelay: PeerConflictRelay,
    private val fileTransferPreferences: FileTransferPreferences,
    private val onPeerChosen: (PeerTransferComponent) -> Unit,
) : ComponentContext by componentContext {
    val peerId: PeerIdentity = peer.id
    private val mutablePeer = MutableStateFlow(peer)

    internal fun updatePeer(peer: Peer) {
        require(peer.id == peerId) { "updatePeer must not change peer identity" }
        mutablePeer.update { peer }
    }

    // TODO(#332): read from peer.device.deviceType once Device carries the field
    val deviceType: DeviceType? = null

    fun destroyContext() {
        lifecycleRegistry.destroy()
    }

    private val showDetailsCallback = onShowDetails
    private val expanded = MutableStateFlow(false)
    private val largeConfirm = MutableStateFlow<PendingLargeConfirm?>(null)

    private val mutableState = MutableValue(
        PeerCardState(
            transfer = engine.state.value,
            expanded = expanded.value,
            largeConfirm = largeConfirm.value,
            isOnline = peer.isOnline,
            device = peer.device,
        ),
    )
    val state: Value<PeerCardState> = mutableState

    init {
        combine(
            engine.state,
            expanded,
            largeConfirm,
            mutablePeer,
        ) { transfer, exp, confirm, peer ->
            mutableState.update {
                PeerCardState(
                    transfer = transfer,
                    expanded = exp,
                    largeConfirm = confirm,
                    isOnline = peer.isOnline,
                    device = peer.device,
                )
            }
        }.launchIn(scope)
    }

    fun startOutbound(sources: List<FileSource>) = engine.startOutbound(sources)

    fun onCardClick() {
        if (engine.state.value !is PeerTransferState.Idle) return

        val pending = pendingFilesRepository.pending.value
        if (pending != null) {
            sendOrConfirmLarge(pending.sources, clearOnSuccess = pending)
            return
        }

        onPeerChosen(this)
    }

    // All access is main-thread-confined: onPick is called from UI and the coroutine body
    // + finally resume on Dispatchers.Main.immediate, so no volatile/atomic needed.
    private var pickInFlight = false

    fun onPick(kind: PickKind) {
        if (pickInFlight) return
        pickInFlight = true
        scope.launch {
            try {
                val sources = when (kind) {
                    PickKind.Files -> filePicker.pickFiles()
                    PickKind.Folder -> filePicker.pickFolder()
                    PickKind.Photos -> filePicker.pickPhotos()
                }
                if (sources.isEmpty()) return@launch
                sendOrConfirmLarge(sources)
            } finally {
                pickInFlight = false
            }
        }
    }

    fun onConfirmLargeSelection(dontShowAgain: Boolean) {
        val confirm = largeConfirm.value ?: return
        largeConfirm.value = null
        if (dontShowAgain) {
            scope.launch { fileTransferPreferences.setLargeSelectionWarning(false) }
        }
        dispatchSend(confirm.sources)
    }

    fun onUpdateLargeConfirmDontShowAgain(checked: Boolean) {
        largeConfirm.update { it?.copy(dontShowAgain = checked) }
    }

    fun onDismissLargeSelection() {
        largeConfirm.value = null
    }

    private fun sendOrConfirmLarge(
        sources: List<FileSource>,
        clearOnSuccess: Pending? = null,
    ) {
        scope.launch {
            val warningEnabled = fileTransferPreferences.observeLargeSelectionWarning().first()
            val summary = PendingFilesSummary.from(sources)
            if (warningEnabled && summary.isLargeSelection) {
                largeConfirm.value = PendingLargeConfirm(sources, summary)
            } else {
                dispatchSend(sources, clearOnSuccess)
            }
        }
    }

    private fun dispatchSend(
        sources: List<FileSource>,
        clearOnSuccess: Pending? = null,
    ) {
        val accepted = engine.startOutbound(sources)
        if (accepted) {
            clearOnSuccess?.let { pendingFilesRepository.clearIfMatches(it) }
        } else {
            conflictRelay.reportBusyTap(peerId)
        }
    }

    fun onCancel() = engine.onCancel()

    fun onRetryOutbound() = engine.onRetryOutbound()

    fun onRetryFile(name: String) = engine.onRetryFile(name)

    fun onCancelFile(name: String) = engine.onCancelFile(name)

    fun onDismiss() = engine.onDismiss()

    fun toggleExpanded() = expanded.update { !it }

    fun observeAutoSend(): Flow<Boolean> = engine.observeAutoSend()

    fun setAutoSend(enabled: Boolean) = engine.setAutoSend(enabled)

    fun onShowDetails() = showDetailsCallback(peerId)
}
