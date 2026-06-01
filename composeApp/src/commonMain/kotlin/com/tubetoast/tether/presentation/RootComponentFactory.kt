package com.tubetoast.tether.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.withLifecycle
import com.tubetoast.tether.peer.PeersRepository
import com.tubetoast.tether.presentation.banners.BannersComponent
import com.tubetoast.tether.presentation.banners.PeerConflictRelay
import com.tubetoast.tether.presentation.transfer.PeerTransferComponent
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.transfer.PeerTransferEngineRegistry
import com.tubetoast.tether.transfer.PendingFilesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class RootComponentFactory(
    private val peersRepository: PeersRepository,
    private val peerTransferEngineRegistry: PeerTransferEngineRegistry,
    private val pendingFilesRepository: PendingFilesRepository,
    private val peerConflictRelay: PeerConflictRelay,
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
                            conflictRelay = peerConflictRelay,
                        )
                    },
                    bannersComponentFactory = { bannersCtx ->
                        BannersComponent(
                            componentContext = bannersCtx,
                            pendingFilesRepository = pendingFilesRepository,
                            peersRepository = peersRepository,
                            engineRegistry = peerTransferEngineRegistry,
                            conflictRelay = peerConflictRelay,
                        )
                    },
                )
            },
        )
}
