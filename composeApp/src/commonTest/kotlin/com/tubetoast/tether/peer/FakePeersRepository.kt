package com.tubetoast.tether.peer

import com.tubetoast.tether.discovery.FakeDeviceDiscovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakePeersRepository(
    private val flow: MutableStateFlow<List<Peer>> = MutableStateFlow(emptyList()),
) : PeersRepository(
        discovery = FakeDeviceDiscovery(),
        scope = CoroutineScope(SupervisorJob()),
    ) {
    override val peers: StateFlow<List<Peer>> = flow
}
