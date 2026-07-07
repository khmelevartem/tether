package com.tubetoast.tether.transfer

import com.tubetoast.tether.peer.PeersRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Creates an engine for every discovered peer as soon as it appears, rather than waiting for the
 * first `engineFor` call from the UI — the per-peer inbound event flow has no replay, so an
 * engine that subscribes only on first render misses any inbound event that arrives before the
 * peer's PeerCard is composed. Started only from GUI entry points: the CLI is sender-only, has no
 * PeerCard, and needs no warm-up.
 */
class EngineWarmer(
    private val peersRepository: PeersRepository,
    private val engineRegistry: PeerTransferEngineRegistry,
    private val scope: CoroutineScope,
) {
    fun start() {
        peersRepository.peers
            .onEach { peers -> peers.forEach { peer -> engineRegistry.engineFor(peer.id) } }
            .launchIn(scope)
    }
}
