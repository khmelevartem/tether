package com.tubetoast.tether.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.tubetoast.tether.discovery.DeviceDiscovery
import com.tubetoast.tether.preferences.PeerPreferencesStore
import com.tubetoast.tether.presentation.peer.PeersRepository
import com.tubetoast.tether.presentation.transfer.PeerTransferComponent
import com.tubetoast.tether.transfer.BatchSender
import com.tubetoast.tether.transfer.ReceiveEvent
import com.tubetoast.tether.transfer.ReconnectionTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class RootComponentFactory(
    private val discovery: DeviceDiscovery,
    private val batchSenderFactory: () -> BatchSender,
    private val inboundEvents: Flow<ReceiveEvent> = MutableSharedFlow(),
    private val peerPreferencesStore: PeerPreferencesStore? = null,
) {
    fun create(componentContext: ComponentContext): RootComponent {
        val scope: CoroutineScope = componentContext.coroutineScope()
        val peersRepository = PeersRepository(discovery = discovery, scope = scope)
        return RootComponent(
            componentContext = componentContext,
            peerListFactory = { ctx, onShowDetails ->
                PeerListComponent(
                    componentContext = ctx,
                    peersRepository = peersRepository,
                    peerTransferComponentFactory = { childCtx, peer ->
                        PeerTransferComponent(
                            componentContext = childCtx,
                            peer = peer.id,
                            batchSenderFactory = batchSenderFactory,
                            inboundEvents = inboundEvents,
                            onShowDetails = onShowDetails,
                            reconnectionTimeout = ReconnectionTimeout.DEFAULT,
                            scope = scope,
                        )
                    },
                    peerPreferencesStore = peerPreferencesStore,
                )
            },
            coroutineScope = scope,
        )
    }
}
