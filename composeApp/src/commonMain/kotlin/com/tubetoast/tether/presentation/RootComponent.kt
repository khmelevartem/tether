package com.tubetoast.tether.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.value.Value
import com.tubetoast.tether.presentation.transfer.TransferDetailsComponent
import com.tubetoast.tether.transfer.PeerIdentity

class RootComponent(
    componentContext: ComponentContext,
    private val peerListFactory: (ComponentContext, onShowDetails: (PeerIdentity) -> Unit) -> PeerListComponent,
) : ComponentContext by componentContext {
    private sealed interface Config {
        data object PeerList : Config

        data class TransferDetails(
            val peer: PeerIdentity,
        ) : Config
    }

    private val navigation = StackNavigation<Config>()

    val peerListComponent: PeerListComponent =
        peerListFactory(childContext("peer_list"), ::showTransferDetails)

    val stack: Value<ChildStack<*, Child>> = childStack(
        source = navigation,
        serializer = null,
        initialConfiguration = Config.PeerList,
        handleBackButton = true,
        childFactory = ::createChild,
    )

    private fun createChild(config: Config, context: ComponentContext): Child =
        when (config) {
            Config.PeerList -> Child.PeerListChild(peerListComponent)
            is Config.TransferDetails -> {
                val peerComponent = peerListComponent.peerTransferComponent(config.peer)
                if (peerComponent == null) {
                    navigation.pop()
                    Child.PeerListChild(peerListComponent)
                } else {
                    Child.TransferDetailsChild(
                        TransferDetailsComponent(
                            componentContext = context,
                            peerComponent = peerComponent,
                            onBack = { navigation.pop() },
                        ),
                    )
                }
            }
        }

    fun showTransferDetails(peer: PeerIdentity) {
        navigation.pushNew(Config.TransferDetails(peer))
    }

    sealed interface Child {
        data class PeerListChild(
            val component: PeerListComponent,
        ) : Child

        data class TransferDetailsChild(
            val component: TransferDetailsComponent,
        ) : Child
    }
}
