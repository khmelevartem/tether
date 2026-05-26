package com.tubetoast.tether.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.tubetoast.tether.presentation.transfer.TransferDetailsComponent
import com.tubetoast.tether.transfer.FileSource
import com.tubetoast.tether.transfer.PeerIdentity

class PendingFilesSummary {
    companion object {
        val NONE = PendingFilesSummary()
    }
}

class RootComponent(
    componentContext: ComponentContext,
    private val deviceListFactory: (ComponentContext) -> DeviceListComponent,
    private val transferDetailsFactory: (ComponentContext, PeerIdentity) -> TransferDetailsComponent,
) : ComponentContext by componentContext {
    private sealed interface Config {
        data object DeviceList : Config

        data class TransferDetails(
            val peer: PeerIdentity,
        ) : Config
    }

    private val navigation = StackNavigation<Config>()

    val stack: Value<ChildStack<*, Child>> = childStack(
        source = navigation,
        serializer = null,
        initialConfiguration = Config.DeviceList,
        handleBackButton = true,
        childFactory = ::createChild,
    )

    private val _pendingFiles = MutableValue(PendingFilesSummary.NONE)
    val pendingFiles: Value<PendingFilesSummary> = _pendingFiles

    private fun createChild(config: Config, context: ComponentContext): Child =
        when (config) {
            Config.DeviceList -> Child.DeviceListChild(deviceListFactory(context))
            is Config.TransferDetails -> Child.TransferDetailsChild(
                transferDetailsFactory(context, config.peer),
            )
        }

    @Suppress("UNUSED_PARAMETER") // TODO(#191): sources will be consumed when the UI reader lands.
    fun setPendingFiles(summary: PendingFilesSummary, sources: List<FileSource>) {
        _pendingFiles.value = summary
    }

    fun clearPendingFiles() {
        _pendingFiles.value = PendingFilesSummary.NONE
    }

    fun showTransferDetails(peer: PeerIdentity) {
        navigation.pushNew(Config.TransferDetails(peer))
    }

    // TODO(#191): UI feedback when user drops files during an active transfer.
    fun onDropRejectedDuringTransfer() {
    }

    sealed interface Child {
        data class DeviceListChild(
            val component: DeviceListComponent,
        ) : Child

        data class TransferDetailsChild(
            val component: TransferDetailsComponent,
        ) : Child
    }
}
