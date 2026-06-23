package com.tubetoast.tether.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.tubetoast.tether.presentation.settings.SettingsComponent
import com.tubetoast.tether.presentation.transfer.TransferDetailsComponent
import com.tubetoast.tether.transfer.FileSource
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.transfer.PendingFilesRepository

class RootComponent(
    componentContext: ComponentContext,
    private val pendingFilesRepository: PendingFilesRepository,
    private val peerListFactory:
        (ComponentContext, onShowDetails: (PeerIdentity) -> Unit, onOpenSettings: () -> Unit) -> PeerListComponent,
    private val settingsFactory: (ComponentContext, onBack: () -> Unit) -> SettingsComponent,
) : ComponentContext by componentContext {
    private sealed interface Config {
        data object PeerList : Config

        data class TransferDetails(
            val peer: PeerIdentity,
        ) : Config

        data object Settings : Config
    }

    private val navigation = StackNavigation<Config>()

    private val _dragActive = MutableValue(false)
    val dragActive: Value<Boolean> = _dragActive

    val peerListComponent: PeerListComponent =
        peerListFactory(childContext("peer_list"), ::showTransferDetails, ::openSettings)

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
            Config.Settings -> Child.SettingsChild(
                settingsFactory(context) { navigation.pop() },
            )
        }

    fun onDragEntered() {
        _dragActive.update { true }
    }

    fun onDragExited() {
        _dragActive.update { false }
    }

    fun onFilesDropped(sources: List<FileSource>) {
        _dragActive.update { false }
        if (sources.isEmpty()) return
        pendingFilesRepository.setPending(sources)
    }

    fun showTransferDetails(peer: PeerIdentity) {
        navigation.pushNew(Config.TransferDetails(peer))
    }

    fun openSettings() {
        navigation.pushNew(Config.Settings)
    }

    sealed interface Child {
        data class PeerListChild(
            val component: PeerListComponent,
        ) : Child

        data class TransferDetailsChild(
            val component: TransferDetailsComponent,
        ) : Child

        data class SettingsChild(
            val component: SettingsComponent,
        ) : Child
    }
}
