package com.tubetoast.tether.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.value.Value
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PendingFilesSummary

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

    private val _pendingFiles = MutableStateFlow<PendingFilesSummary?>(null)
    val pendingFiles: StateFlow<PendingFilesSummary?> = _pendingFiles

    private fun createChild(config: Config, context: ComponentContext): Child =
        when (config) {
            Config.DeviceList -> Child.DeviceListChild(deviceListFactory(context))
            is Config.TransferDetails -> Child.TransferDetailsChild(
                transferDetailsFactory(context, config.peer),
            )
        }

    fun setPendingFiles(summary: PendingFilesSummary) {
        _pendingFiles.value = summary
    }

    fun clearPendingFiles() {
        _pendingFiles.value = null
    }

    fun showTransferDetails(peer: PeerIdentity) {
        navigation.pushNew(Config.TransferDetails(peer))
    }

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
