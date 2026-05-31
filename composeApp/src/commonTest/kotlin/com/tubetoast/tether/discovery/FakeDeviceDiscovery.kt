package com.tubetoast.tether.discovery

import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeDeviceDiscovery(
    private val flow: StateFlow<List<Device>> = MutableStateFlow(emptyList()),
) : DeviceDiscovery {
    override val discoveredDevices: StateFlow<List<Device>> = flow

    val republishCalls = mutableListOf<String>()

    override suspend fun start(deviceName: String, port: Int) = Unit

    override suspend fun stop() = Unit

    override suspend fun republish(name: String) {
        republishCalls.add(name)
    }
}
