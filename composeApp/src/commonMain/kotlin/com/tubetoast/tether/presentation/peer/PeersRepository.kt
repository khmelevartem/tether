package com.tubetoast.tether.presentation.peer

import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.tubetoast.tether.discovery.DeviceDiscovery
import com.tubetoast.tether.transfer.toPeerIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class PeersRepository(
    discovery: DeviceDiscovery,
    scope: CoroutineScope,
) {
    private val _peers = MutableValue<List<Peer>>(emptyList())
    val peers: Value<List<Peer>> = _peers

    init {
        scope.launch {
            discovery.discoveredDevices.collect { devices ->
                _peers.update {
                    devices.map { device -> Peer(id = device.toPeerIdentity(), device = device) }
                }
            }
        }
    }
}
