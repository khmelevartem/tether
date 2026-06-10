package com.tubetoast.tether.presentation.banners

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.tubetoast.tether.peer.PeersRepository
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.transfer.PeerTransferEngineRegistry
import com.tubetoast.tether.transfer.PeerTransferState
import com.tubetoast.tether.transfer.PendingFilesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BannersComponent(
    componentContext: ComponentContext,
    private val pendingFilesRepository: PendingFilesRepository,
    private val peersRepository: PeersRepository,
    private val engineRegistry: PeerTransferEngineRegistry,
    private val conflictRelay: PeerConflictRelay,
    transferActive: StateFlow<Boolean> = MutableStateFlow(false),
    coroutineScope: CoroutineScope = componentContext.coroutineScope(),
) : ComponentContext by componentContext {
    private val scope = coroutineScope

    val dropFeedback: StateFlow<Boolean> get() = _dropFeedback
    private val _dropFeedback = MutableStateFlow(false)

    val showForegroundConstraint: StateFlow<Boolean> = transferActive

    private val selectedConflictPeer = MutableStateFlow<PeerIdentity?>(null)

    val pendingBanner: StateFlow<PendingOutboundBannerState> = buildPendingBannerFlow()

    private var dropFeedbackJob: Job? = null

    init {
        collectBusyTaps()
        resetOnPendingCleared()
        resetOnEngineIdle()
    }

    fun onCancelPending() {
        pendingFilesRepository.clear()
    }

    fun onDropDuringActiveTransfer() {
        dropFeedbackJob?.cancel()
        dropFeedbackJob = scope.launch {
            _dropFeedback.update { true }
            delay(DROP_FEEDBACK_DURATION_MS)
            _dropFeedback.update { false }
        }
    }

    private fun collectBusyTaps() {
        conflictRelay
            .busyTaps
            .onEach { peerId ->
                if (pendingFilesRepository.pending.value != null) {
                    selectedConflictPeer.update { peerId }
                }
            }.launchIn(scope)
    }

    private fun resetOnPendingCleared() {
        pendingFilesRepository.pending
            .onEach { pending -> if (pending == null) selectedConflictPeer.update { null } }
            .launchIn(scope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun resetOnEngineIdle() {
        val engineStateFlow = selectedConflictPeer.flatMapLatest { conflictPeerEngineStateFlow(it) }
        engineStateFlow
            .onEach { state -> if (state is PeerTransferState.Idle) selectedConflictPeer.update { null } }
            .launchIn(scope)
    }

    private fun conflictPeerName(peerId: PeerIdentity): String =
        peersRepository
            .peers
            .value
            .firstOrNull { it.id == peerId }
            ?.device
            ?.name ?: peerId.id

    private fun conflictPeerEngineStateFlow(peerId: PeerIdentity?) =
        if (peerId != null) {
            engineRegistry.engineFor(peerId).state
        } else {
            flowOf(null)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun buildPendingBannerFlow(): StateFlow<PendingOutboundBannerState> {
        val engineStateFlow = selectedConflictPeer.flatMapLatest { conflictPeerEngineStateFlow(it) }
        return combine(
            pendingFilesRepository.pending,
            selectedConflictPeer,
            _dropFeedback,
            engineStateFlow,
        ) { pending, peerId, dropFeedback, engineState ->
            if (pending == null) {
                PendingOutboundBannerState.Hidden
            } else if (peerId == null) {
                PendingOutboundBannerState.Default(pending.summary, dropFeedback)
            } else {
                val peerName = conflictPeerName(peerId)
                when (engineState) {
                    is PeerTransferState.ActiveOutbound,
                    is PeerTransferState.ActiveInbound,
                    is PeerTransferState.Reconnecting,
                    -> PendingOutboundBannerState.BusyPeer(pending.summary, peerName)

                    is PeerTransferState.Sent,
                    is PeerTransferState.Received,
                    is PeerTransferState.Error,
                    is PeerTransferState.Cancelled,
                    -> PendingOutboundBannerState.TerminalDisplay(peerName)

                    is PeerTransferState.Idle,
                    null,
                    -> PendingOutboundBannerState.Default(pending.summary, dropFeedback)
                }
            }
        }.stateIn(scope, SharingStarted.Eagerly, PendingOutboundBannerState.Hidden)
    }

    private companion object {
        const val DROP_FEEDBACK_DURATION_MS = 3_000L
    }
}
