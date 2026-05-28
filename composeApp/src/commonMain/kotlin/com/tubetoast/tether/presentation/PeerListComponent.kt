package com.tubetoast.tether.presentation

import com.arkivanov.decompose.Cancellation
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.tubetoast.tether.presentation.peer.Peer
import com.tubetoast.tether.presentation.peer.PeersRepository
import com.tubetoast.tether.presentation.transfer.PeerTransferComponent
import com.tubetoast.tether.presentation.transfer.PeerTransferState
import com.tubetoast.tether.transfer.PeerIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class PeerListComponent(
    componentContext: ComponentContext,
    private val peersRepository: PeersRepository,
    private val peerTransferComponentFactory: (ComponentContext, Peer) -> PeerTransferComponent,
    coroutineScope: CoroutineScope = componentContext.coroutineScope(),
) : ComponentContext by componentContext {
    private val _state = MutableValue(PeerListState.empty())
    val state: Value<PeerListState> = _state

    private val children = mutableMapOf<PeerIdentity, PeerTransferComponent>()
    private val subscriptions = mutableMapOf<PeerIdentity, Cancellation>()
    private val seenPeers = mutableMapOf<PeerIdentity, Peer>()
    private var onlineIds: Set<PeerIdentity> = emptySet()

    init {
        coroutineScope.launch {
            peersRepository.peers.subscribe { peers ->
                onlineIds = peers.map { it.id }.toSet()
                peers.forEach { peer -> seenPeers[peer.id] = peer }
                ensureChildrenFor(peers)
                evictOfflineIdlePeers()
                rebuildState()
            }
        }
    }

    fun peerTransferComponent(peer: PeerIdentity): PeerTransferComponent? = children[peer]

    private fun ensureChildrenFor(peers: List<Peer>) {
        peers.forEach { peer ->
            if (peer.id !in children) {
                val child = peerTransferComponentFactory(childContext(peer.id.id), peer)
                children[peer.id] = child
                subscriptions[peer.id] = child.state.subscribe {
                    evictOfflineIdlePeers()
                    rebuildState()
                }
            }
        }
    }

    private fun evictOfflineIdlePeers() {
        val toRemove = children.keys.filter { id ->
            id !in onlineIds && children[id]?.state?.value is PeerTransferState.Idle
        }
        toRemove.forEach { id ->
            subscriptions.remove(id)?.cancel()
            children.remove(id)
        }
    }

    private fun rebuildState() {
        val rows = children.mapNotNull { (id, child) ->
            val peer = seenPeers[id] ?: return@mapNotNull null
            PeerRow(
                peer = peer.copy(isOnline = id in onlineIds),
                transferState = child.state.value,
            )
        }
        _state.update { PeerListState(rows) }
    }
}
