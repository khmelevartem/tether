package com.tubetoast.tether.presentation.transfer

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.tubetoast.tether.presentation.peer.Peer
import com.tubetoast.tether.transfer.FileSource
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.transfer.PeerTransferEngine
import com.tubetoast.tether.transfer.PeerTransferState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

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
) : ComponentContext by componentContext {
    fun destroyContext() {
        lifecycleRegistry.destroy()
    }

    private val showDetailsCallback = onShowDetails
    private val mutableState = MutableValue<PeerTransferState>(engine.state.value)
    val state: Value<PeerTransferState> = mutableState

    init {
        engine.state
            .onEach { s -> mutableState.update { s } }
            .launchIn(scope)
    }

    fun startOutbound(sources: List<FileSource>) = engine.startOutbound(sources)

    fun onCardClick() {
        val sources = pendingFilesRepository?.sources?.value.orEmpty()
        if (sources.isNotEmpty()) {
            engine.startOutbound(sources)
            pendingFilesRepository?.clear()
        } else {
            onOpenPicker()
        }
    }

    fun onCancel() = engine.onCancel()

    fun onRetry() = engine.onRetry()

    fun onRetryFile(name: String) = engine.onRetryFile(name)

    fun onCancelFile(name: String) = engine.onCancelFile(name)

    fun onDismiss() = engine.onDismiss()

    fun toggleExpanded() = engine.toggleExpanded()

    fun observeAutoSend(): Flow<Boolean> = engine.observeAutoSend()

    fun setAutoSend(enabled: Boolean) = engine.setAutoSend(enabled)

    fun onShowDetails() = showDetailsCallback(peer.id)
}
