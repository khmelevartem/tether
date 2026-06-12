package com.tubetoast.tether.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.childContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arkivanov.essenty.lifecycle.resume
import com.tubetoast.tether.peer.Peer
import com.tubetoast.tether.peer.PeersRepository
import com.tubetoast.tether.presentation.banners.BannersComponent
import com.tubetoast.tether.presentation.devicename.DeviceNameComponent
import com.tubetoast.tether.presentation.transfer.PeerTransferComponent
import com.tubetoast.tether.transfer.PeerIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class PeerListComponent(
    componentContext: ComponentContext,
    private val peersRepository: PeersRepository,
    private val peerTransferComponentFactory: (ComponentContext, LifecycleRegistry, Peer) -> PeerTransferComponent,
    bannersComponentFactory: (ComponentContext) -> BannersComponent,
    deviceNameComponentFactory: (ComponentContext) -> DeviceNameComponent,
    coroutineScope: CoroutineScope = componentContext.coroutineScope(),
) : ComponentContext by componentContext {
    val bannersComponent: BannersComponent = bannersComponentFactory(childContext("banners"))
    val deviceNameComponent: DeviceNameComponent = deviceNameComponentFactory(childContext("deviceName"))
    private val _state = MutableValue(PeerListState.empty())
    val state: Value<PeerListState> = _state

    init {
        peersRepository.peers
            .onEach { peers ->
                val previous = _state.value.rows.associateBy { it.transferComponent.peerId }
                val newIds = peers.map { it.id }.toSet()

                previous.values
                    .filter { it.transferComponent.peerId !in newIds }
                    .forEach { it.transferComponent.destroyContext() }

                val newRows = peers.map { peer ->
                    val existing = previous[peer.id]?.transferComponent
                    if (existing != null) {
                        existing.updatePeer(peer)
                        PeerRow(existing)
                    } else {
                        PeerRow(createComponent(peer))
                    }
                }
                _state.update { PeerListState(rows = newRows) }
            }.launchIn(coroutineScope)
    }

    fun peerTransferComponent(peer: PeerIdentity): PeerTransferComponent? =
        _state.value.rows
            .firstOrNull { it.transferComponent.peerId == peer }
            ?.transferComponent

    private fun createComponent(peer: Peer): PeerTransferComponent {
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        return peerTransferComponentFactory(DefaultComponentContext(lifecycle), lifecycle, peer)
    }
}
