package com.tubetoast.tether.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.withLifecycle
import com.tubetoast.tether.presentation.banners.BannersComponent
import com.tubetoast.tether.presentation.peer.PeersRepository
import com.tubetoast.tether.presentation.transfer.PeerTransferComponent
import com.tubetoast.tether.presentation.transfer.PendingFilesRepository
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.transfer.PeerTransferEngineRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class RootComponentFactory(
    private val peersRepository: PeersRepository,
    private val peerTransferEngineRegistry: PeerTransferEngineRegistry,
    private val pendingFilesRepository: PendingFilesRepository,
    private val onPickerPick: (PeerIdentity) -> Unit = {},
) {
    fun create(componentContext: ComponentContext): RootComponent =
        RootComponent(
            componentContext = componentContext,
            peerListFactory = { ctx, onShowDetails ->
                PeerListComponent(
                    componentContext = ctx,
                    peersRepository = peersRepository,
                    peerTransferComponentFactory = { childCtx, lifecycle, peer ->
                        val engine = peerTransferEngineRegistry.engineFor(peer.id)
                        val componentScope = CoroutineScope(Dispatchers.Main.immediate).withLifecycle(lifecycle)
                        PeerTransferComponent(
                            componentContext = childCtx,
                            peer = peer,
                            lifecycleRegistry = lifecycle,
                            engine = engine,
                            scope = componentScope,
                            onShowDetails = onShowDetails,
                            pendingFilesRepository = pendingFilesRepository,
                            onOpenPicker = { onPickerPick(peer.id) },
                        )
                    },
                    bannersComponentFactory = { bannersCtx -> BannersComponent(bannersCtx, pendingFilesRepository) },
                )
            },
        )
}
