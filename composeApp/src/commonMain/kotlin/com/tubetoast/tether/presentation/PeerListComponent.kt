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
import com.tubetoast.tether.foundation.IsPickerModeChooserNeeded
import com.tubetoast.tether.peer.Peer
import com.tubetoast.tether.peer.PeersRepository
import com.tubetoast.tether.presentation.banners.BannersComponent
import com.tubetoast.tether.presentation.devicename.DeviceNameComponent
import com.tubetoast.tether.presentation.transfer.PeerTransferComponent
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.transfer.PickKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class PeerListComponent(
    componentContext: ComponentContext,
    private val peersRepository: PeersRepository,
    private val peerTransferComponentFactory:
        (ComponentContext, LifecycleRegistry, Peer, PeerListener) -> PeerTransferComponent,
    bannersComponentFactory: (ComponentContext) -> BannersComponent,
    deviceNameComponentFactory: (ComponentContext) -> DeviceNameComponent,
    private val isPickerModeChooserNeeded: Boolean = IsPickerModeChooserNeeded,
    coroutineScope: CoroutineScope = componentContext.coroutineScope(),
) : ComponentContext by componentContext {
    val bannersComponent: BannersComponent = bannersComponentFactory(childContext("banners"))
    val deviceNameComponent: DeviceNameComponent = deviceNameComponentFactory(childContext("deviceName"))
    private val _state = MutableValue(PeerListState.empty())
    val state: Value<PeerListState> = _state

    private var pendingPickComponent: PeerTransferComponent? = null

    init {
        peersRepository.peers
            .onEach { peers ->
                val previous = _state.value.rows.associateBy { it.peerId }
                val newIds = peers.map { it.id }.toSet()

                val evicted = previous.values.filter { it.peerId !in newIds }
                evicted.forEach { it.destroyContext() }
                if (pendingPickComponent in evicted) {
                    onChoosePickerMode(null)
                }

                val newRows = peers.map { peer ->
                    val existing = previous[peer.id]
                    if (existing != null) {
                        existing.updatePeer(peer)
                        existing
                    } else {
                        createComponent(peer)
                    }
                }
                _state.update { it.copy(rows = newRows) }
            }.launchIn(coroutineScope)
    }

    fun peerTransferComponent(peer: PeerIdentity): PeerTransferComponent? =
        _state.value.rows.firstOrNull { it.peerId == peer }

    fun onChoosePickerMode(mode: PickKind?) {
        _state.update {
            it.copy(showPickerModeChooser = false)
        }
        mode?.let {
            pendingPickComponent?.onPick(mode)
                ?: throw IllegalStateException("Choosing picker mode is unavailable before picking peer in current UX")
        }
        pendingPickComponent = null
    }

    private fun createComponent(peer: Peer): PeerTransferComponent {
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        return peerTransferComponentFactory(DefaultComponentContext(lifecycle), lifecycle, peer, ::onPeerChosen)
    }

    private fun onPeerChosen(peer: PeerTransferComponent?) {
        pendingPickComponent = peer
        if (isPickerModeChooserNeeded) {
            _state.update {
                it.copy(showPickerModeChooser = true)
            }
        } else {
            onChoosePickerMode(PickKind.Files)
        }
    }
}

private typealias PeerListener = (PeerTransferComponent) -> Unit
