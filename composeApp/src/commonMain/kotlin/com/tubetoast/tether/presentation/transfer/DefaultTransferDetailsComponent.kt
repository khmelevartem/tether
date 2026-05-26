package com.tubetoast.tether.presentation.transfer

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value

class DefaultTransferDetailsComponent(
    componentContext: ComponentContext,
    private val peerComponent: PeerTransferComponent,
    private val onBack: () -> Unit,
) : TransferDetailsComponent,
    ComponentContext by componentContext {
    override val state: Value<PeerTransferState> get() = peerComponent.state

    override fun onRetryFile(name: String) = peerComponent.onRetryFile(name)

    override fun onCancelFile(name: String) = peerComponent.onCancelFile(name)

    override fun onCancelTransfer() = peerComponent.onCancel()

    override fun onRetryAll() = peerComponent.onRetry()

    override fun onBack() = onBack.invoke()
}
