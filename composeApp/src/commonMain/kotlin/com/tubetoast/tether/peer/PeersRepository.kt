package com.tubetoast.tether.peer

import com.tubetoast.tether.discovery.DeviceDiscovery
import com.tubetoast.tether.transfer.toPeerIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

open class PeersRepository(
    discovery: DeviceDiscovery,
    scope: CoroutineScope,
) {
    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    open val peers: StateFlow<List<Peer>> = _peers.asStateFlow()

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
