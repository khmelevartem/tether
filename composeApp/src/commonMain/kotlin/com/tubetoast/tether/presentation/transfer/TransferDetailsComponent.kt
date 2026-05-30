package com.tubetoast.tether.presentation.transfer

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value

class TransferDetailsComponent(
    componentContext: ComponentContext,
    private val peerComponent: PeerTransferComponent,
    private val onBack: () -> Unit,
) : ComponentContext by componentContext {
    val state: Value<PeerCardState> get() = peerComponent.state

    fun onRetryFile(name: String) = peerComponent.onRetryFile(name)

    fun onCancelFile(name: String) = peerComponent.onCancelFile(name)

    fun onCancelTransfer() = peerComponent.onCancel()

    fun onRetryAll() = peerComponent.onRetry()

    fun onBack() = onBack.invoke()
}
