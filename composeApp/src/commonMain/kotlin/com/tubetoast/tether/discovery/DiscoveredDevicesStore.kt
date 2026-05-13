package com.tubetoast.tether.discovery

import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Insertion-ordered store for discovered peers. Thread-safe — observers see a
 * consistent snapshot.
 */
class DiscoveredDevicesStore {
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    /**
     * Adds or replaces by [Device.id]. Any other entry with the same
     * [Device.name] but a different id is evicted in the same atomic step —
     * mDNS re-resolution of the same service with a new address (port or IP
     * change without a separate "lost" callback) yields a different id, and
     * the previous one becomes stale.
     */
    fun upsert(device: Device) {
        _devices.update { prev ->
            val result = ArrayList<Device>(prev.size + 1)
            var replaced = false
            for (existing in prev) {
                when {
                    existing.id == device.id -> {
                        result += device
                        replaced = true
                    }
                    existing.name == device.name -> Unit
                    else -> result += existing
                }
            }
            if (!replaced) result += device
            result
        }
    }

    /**
     * Removes every entry whose [Device.name] equals [name]. Platform mDNS
     * "service lost" callbacks (Android NSD, JmDNS, Bonjour, NSNetService)
     * identify the disappearing peer by service name only — host/port are not
     * preserved past resolution.
     */
    fun removeByName(name: String) {
        _devices.update { prev -> prev.filter { it.name != name } }
    }

    fun clear() {
        _devices.update { emptyList() }
    }
}
