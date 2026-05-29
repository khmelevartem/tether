package com.tubetoast.tether.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.tubetoast.tether.preferences.PeerPreferencesStore
import com.tubetoast.tether.presentation.banners.BannersComponent
import com.tubetoast.tether.presentation.peer.PeersRepository
import com.tubetoast.tether.presentation.transfer.PeerTransferComponent
import com.tubetoast.tether.presentation.transfer.PendingFilesRepository
import com.tubetoast.tether.transfer.BatchSender
import com.tubetoast.tether.transfer.ReconnectionTimeout

class RootComponentFactory(
    private val peersRepository: PeersRepository,
    private val batchSenderFactory: () -> BatchSender,
    private val pendingFilesRepository: PendingFilesRepository,
    private val peerPreferencesStore: PeerPreferencesStore? = null,
) {
    fun create(componentContext: ComponentContext): RootComponent =
        RootComponent(
            componentContext = componentContext,
            bannersFactory = { ctx -> BannersComponent(ctx, pendingFilesRepository) },
            peerListFactory = { ctx, onShowDetails ->
                PeerListComponent(
                    componentContext = ctx,
                    peersRepository = peersRepository,
                    peerTransferComponentFactory = { childCtx, peer ->
                        PeerTransferComponent(
                            componentContext = childCtx,
                            peer = peer.id,
                            batchSenderFactory = batchSenderFactory,
                            onShowDetails = onShowDetails,
                            reconnectionTimeout = ReconnectionTimeout.DEFAULT,
                            scope = childCtx.coroutineScope(),
                        )
                    },
                    pendingFilesRepository = pendingFilesRepository,
                    peerPreferencesStore = peerPreferencesStore,
                )
            },
        )
}
