package com.tubetoast.tether.presentation.transfer

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.PeerIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

data class PeerRowProjection(
    val state: PeerTransferState,
    val isOnline: Boolean,
)

class TransferRegistry(
    componentContext: ComponentContext,
    private val peerComponentFactory: (ComponentContext, PeerIdentity) -> PeerTransferComponent,
    private val discoveredDevices: Flow<List<Device>>,
    private val scope: CoroutineScope,
) : ComponentContext by componentContext {
    private val components = mutableMapOf<PeerIdentity, PeerTransferComponent>()
    private val _rows = MutableValue<Map<PeerIdentity, PeerRowProjection>>(emptyMap())
    val rows: Value<Map<PeerIdentity, PeerRowProjection>> = _rows

    private var onlineIds: Set<PeerIdentity> = emptySet()

    init {
        scope.launch {
            discoveredDevices.collect { devices ->
                val ids = devices.map { it.toPeerIdentity() }.toSet()
                onlineIds = ids
                evict(ids)
                rebuildRows(ids)
            }
        }
    }

    fun get(peer: PeerIdentity): PeerTransferComponent {
        val existing = components[peer]
        if (existing != null) return existing
        val child = peerComponentFactory(childContext(peer.id), peer)
        components[peer] = child
        child.state.subscribe { rebuildRows(onlineIds) }
        rebuildRows(onlineIds)
        return child
    }

    private fun evict(onlineIds: Set<PeerIdentity>) {
        val toRemove = components.keys.filter { peer ->
            peer !in onlineIds && components[peer]?.state?.value is PeerTransferState.Idle
        }
        toRemove.forEach { components.remove(it) }
    }

    private fun rebuildRows(onlineIds: Set<PeerIdentity>) {
        _rows.update {
            components.mapValues { (peer, component) ->
                PeerRowProjection(
                    state = component.state.value,
                    isOnline = peer in onlineIds,
                )
            }
        }
    }
}

fun Device.toPeerIdentity(): PeerIdentity = PeerIdentity(id)
