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

    // TODO(#318): drop this lifecycle registry once transfer state lives in a domain
    // repository — the component then has nothing to retain and Decompose contexts can be
    // destroyed on eviction.
    private val components = mutableMapOf<PeerIdentity, PeerTransferComponent>()

    init {
        peersRepository.peers
            .onEach { peers ->
                val emitIds = peers.map { it.id }.toSet()
                peers.forEach { peer ->
                    if (peer.id !in components) {
                        components[peer.id] = peerTransferComponentFactory(childContext(peer.id.id), peer)
                    }
                }
                components.keys
                    .filter { id -> id !in emitIds && components[id]?.state?.value is PeerTransferState.Idle }
                    .forEach { id -> components.remove(id) }
                _state.update {
                    PeerListState(
                        rows = peers.map { peer ->
                            PeerRow(peer = peer, transferComponent = components.getValue(peer.id))
                        },
                    )
                }
            }.launchIn(coroutineScope)
    }

    fun peerTransferComponent(peer: PeerIdentity): PeerTransferComponent? =
        _state.value.rows
            .firstOrNull { it.peer.id == peer }
            ?.transferComponent
}
