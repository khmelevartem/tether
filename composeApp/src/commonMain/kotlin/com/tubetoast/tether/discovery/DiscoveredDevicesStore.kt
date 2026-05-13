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

    /** Adds or replaces by id; same-name entries with different id are evicted atomically. */
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

    fun removeByName(name: String) {
        _devices.update { prev -> prev.filter { it.name != name } }
    }

    fun clear() {
        _devices.update { emptyList() }
    }
}
