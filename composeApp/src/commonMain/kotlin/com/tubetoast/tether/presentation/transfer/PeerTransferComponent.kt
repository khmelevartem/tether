package com.tubetoast.tether.presentation.transfer

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.tubetoast.tether.peer.Peer
import com.tubetoast.tether.transfer.FileSource
import com.tubetoast.tether.transfer.PeerConflictRelay
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.transfer.PeerTransferEngine
import com.tubetoast.tether.transfer.PendingFilesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update

class PeerTransferComponent(
    componentContext: ComponentContext,
    val peer: Peer,
    private val lifecycleRegistry: LifecycleRegistry,
    private val engine: PeerTransferEngine,
    onShowDetails: (PeerIdentity) -> Unit,
    private val scope: CoroutineScope,
    private val pendingFilesRepository: PendingFilesRepository? = null,
    // TODO(#192/#193/#194): platform actuals wire real file picker here
    private val onOpenPicker: () -> Unit = {},
    private val conflictRelay: PeerConflictRelay? = null,
) : ComponentContext by componentContext {
    fun destroyContext() {
        lifecycleRegistry.destroy()
    }

    private val showDetailsCallback = onShowDetails
    private val expanded = MutableStateFlow(false)
    private val mutableState = MutableValue(PeerCardState(engine.state.value, expanded.value))
    val state: Value<PeerCardState> = mutableState

    init {
        combine(engine.state, expanded) { transfer, exp -> mutableState.update { PeerCardState(transfer, exp) } }
            .launchIn(scope)
    }

    fun startOutbound(sources: List<FileSource>) = engine.startOutbound(sources)

    fun onCardClick() {
        val pending = pendingFilesRepository?.pending?.value
        if (pending == null) {
            onOpenPicker()
            return
        }
        val accepted = engine.startOutbound(pending.sources)
        if (accepted) {
            pendingFilesRepository.clearIfMatches(pending)
        } else {
            conflictRelay?.reportBusyTap(peer.id)
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

    fun onShowDetails() = showDetailsCallback(peer.id)
}
