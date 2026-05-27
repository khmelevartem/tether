package com.tubetoast.tether.presentation.transfer

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.tubetoast.tether.transfer.FileSource
import com.tubetoast.tether.transfer.PeerIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class PeerTransferComponent(
    componentContext: ComponentContext,
    val peer: PeerIdentity,
    private val repository: PeerTransferRepository,
    private val onShowDetailsCallback: (PeerIdentity) -> Unit,
    coroutineScope: CoroutineScope = componentContext.coroutineScope(),
) : ComponentContext by componentContext {
    private val mutableState = MutableValue<PeerTransferState>(PeerTransferState.Idle(peer))
    val state: Value<PeerTransferState> = mutableState

    init {
        coroutineScope.launch {
            repository.observe(peer).collect { mutableState.value = it }
        }
    }

    fun startOutbound(sources: List<FileSource>) = repository.startOutbound(peer, sources)

    fun onCancel() = repository.cancel(peer)

    fun onRetry() = repository.retry(peer)

    fun onRetryFile(name: String) = repository.retryFile(peer, name)

    fun onCancelFile(name: String) = repository.cancelFile(peer, name)

    fun onDismiss() = repository.dismiss(peer)

    fun toggleExpanded() = repository.toggleExpanded(peer)

    fun onShowDetails() = onShowDetailsCallback(peer)
}
