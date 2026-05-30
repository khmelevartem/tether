package com.tubetoast.tether.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.withLifecycle
import com.tubetoast.tether.preferences.PeerPreferencesStore
import com.tubetoast.tether.presentation.banners.BannersComponent
import com.tubetoast.tether.presentation.peer.PeersRepository
import com.tubetoast.tether.presentation.transfer.PeerTransferComponent
import com.tubetoast.tether.presentation.transfer.PendingFilesRepository
import com.tubetoast.tether.transfer.BatchSender
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.transfer.PeerTransferEngine
import com.tubetoast.tether.transfer.ReconnectionTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class RootComponentFactory(
    private val peersRepository: PeersRepository,
    private val batchSenderFactory: () -> BatchSender,
    private val pendingFilesRepository: PendingFilesRepository,
    private val peerPreferencesStore: PeerPreferencesStore? = null,
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
                        val scope = CoroutineScope(Dispatchers.Main.immediate).withLifecycle(lifecycle)
                        val engine = PeerTransferEngine(
                            peer = peer.id,
                            batchSenderFactory = batchSenderFactory,
                            reconnectionTimeout = ReconnectionTimeout.DEFAULT,
                            scope = scope,
                            peerPreferencesStore = peerPreferencesStore,
                        )
                        PeerTransferComponent(
                            componentContext = childCtx,
                            peer = peer,
                            lifecycleRegistry = lifecycle,
                            engine = engine,
                            onShowDetails = onShowDetails,
                            scope = scope,
                            pendingFilesRepository = pendingFilesRepository,
                            onOpenPicker = { onPickerPick(peer.id) },
                        )
                    },
                    bannersComponentFactory = { bannersCtx -> BannersComponent(bannersCtx, pendingFilesRepository) },
                )
            },
        )
}
