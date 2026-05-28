package com.tubetoast.tether.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.tubetoast.tether.presentation.transfer.TransferDetailsComponent
import com.tubetoast.tether.transfer.PeerIdentity
import kotlinx.coroutines.CoroutineScope

class RootComponent(
    componentContext: ComponentContext,
    private val peerListFactory: (ComponentContext, onShowDetails: (PeerIdentity) -> Unit) -> PeerListComponent,
    coroutineScope: CoroutineScope = componentContext.coroutineScope(),
) : ComponentContext by componentContext {
    private sealed interface Config {
        data object DeviceList : Config

        data class TransferDetails(
            val peer: PeerIdentity,
        ) : Config
    }

    private val navigation = StackNavigation<Config>()

    private var _peerListComponent: PeerListComponent? = null

    val peerListComponent: PeerListComponent
        get() = requireNotNull(_peerListComponent) { "PeerListComponent not yet created" }

    val stack: Value<ChildStack<*, Child>> = childStack(
        source = navigation,
        serializer = null,
        initialConfiguration = Config.DeviceList,
        handleBackButton = true,
        childFactory = ::createChild,
    )

    private fun createChild(config: Config, context: ComponentContext): Child =
        when (config) {
            Config.DeviceList -> {
                val plc = peerListFactory(context, ::showTransferDetails)
                _peerListComponent = plc
                Child.DeviceListChild(plc)
            }
            is Config.TransferDetails -> {
                val peerComponent = requireNotNull(_peerListComponent?.peerTransferComponent(config.peer)) {
                    "No PeerTransferComponent for ${config.peer} — navigate to device list first"
                }
                Child.TransferDetailsChild(
                    TransferDetailsComponent(
                        componentContext = context,
                        peerComponent = peerComponent,
                        onBack = { navigation.pop() },
                    ),
                )
            }
        }

    fun showTransferDetails(peer: PeerIdentity) {
        navigation.pushNew(Config.TransferDetails(peer))
    }

    sealed interface Child {
        data class DeviceListChild(
            val component: PeerListComponent,
        ) : Child

        data class TransferDetailsChild(
            val component: TransferDetailsComponent,
        ) : Child
    }
}
