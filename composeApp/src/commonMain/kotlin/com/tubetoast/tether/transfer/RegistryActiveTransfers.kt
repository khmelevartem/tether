package com.tubetoast.tether.transfer

import com.tubetoast.tether.discovery.ActiveTransfers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class RegistryActiveTransfers(
    registry: PeerTransferEngineRegistry,
    scope: CoroutineScope,
) : ActiveTransfers {
    override val peers: StateFlow<Set<String>> = registry.engines
        .flatMapLatest { engines ->
            if (engines.isEmpty()) {
                flowOf(emptySet())
            } else {
                combine(engines.map { (peer, engine) -> engine.state.activeFingerprintOrNull(peer) }) { active ->
                    active.filterNotNull().toSet()
                }
            }
        }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    private fun StateFlow<PeerTransferState>.activeFingerprintOrNull(peer: PeerIdentity) =
        map { state -> peer.id.takeIf { state.isActiveTransfer() } }

    private fun PeerTransferState.isActiveTransfer(): Boolean = when (this) {
        is PeerTransferState.ActiveOutbound,
        is PeerTransferState.ActiveInbound,
        is PeerTransferState.Reconnecting,
        -> true
        else -> false
    }
}
