package com.tubetoast.tether.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.tubetoast.tether.presentation.banners.BannersComponent
import com.tubetoast.tether.presentation.peer.Peer
import com.tubetoast.tether.presentation.peer.PeersRepository
import com.tubetoast.tether.presentation.transfer.PeerTransferComponent
import com.tubetoast.tether.presentation.transfer.PeerTransferState
import com.tubetoast.tether.transfer.PeerIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class PeerListComponent(
    componentContext: ComponentContext,
    private val peersRepository: PeersRepository,
    private val peerTransferComponentFactory: (ComponentContext, Peer) -> PeerTransferComponent,
    bannersComponentFactory: (ComponentContext) -> BannersComponent,
    coroutineScope: CoroutineScope = componentContext.coroutineScope(),
) : ComponentContext by componentContext {
    val bannersComponent: BannersComponent = bannersComponentFactory(childContext("banners"))
    private val _state = MutableValue(PeerListState.empty())
    val state: Value<PeerListState> = _state

    private val children = mutableMapOf<PeerIdentity, PeerTransferComponent>()

    init {
        peersRepository.peers
            .onEach { peers ->
                val onlineIds = peers.filter { it.isOnline }.map { it.id }.toSet()
                peers.forEach { peer ->
                    if (peer.id !in children) {
                        children[peer.id] = peerTransferComponentFactory(childContext(peer.id.id), peer)
                    }
                }
                children.keys
                    .filter { id -> id !in onlineIds && children[id]?.state?.value is PeerTransferState.Idle }
                    .forEach { id -> children.remove(id) }
                _state.update {
                    PeerListState(
                        rows = peers.map { peer ->
                            PeerRow(
                                peer = peer,
                                // snapshot refreshed each emit; Compose subscribes to peerComponent.state per cell
                                transferState = children[peer.id]?.state?.value ?: PeerTransferState.Idle(peer.id),
                            )
                        },
                    )
                }
            }.launchIn(coroutineScope)
    }

    fun peerTransferComponent(peer: PeerIdentity): PeerTransferComponent? = children[peer]
}
