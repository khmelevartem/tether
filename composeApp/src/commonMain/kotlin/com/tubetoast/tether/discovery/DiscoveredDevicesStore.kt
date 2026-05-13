package com.tubetoast.tether.discovery

import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Insertion-ordered store for discovered peers. All mutations use [MutableStateFlow.update]
 * which CAS-loops until the snapshot is applied atomically. The list is never mutated in
 * place — each operation produces a fresh copy. Observers always see a consistent snapshot.
 */
class DiscoveredDevicesStore {
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    /**
     * Keys by [Device.id]. Use when id is stable at upsert time (e.g. JmDNS, where
     * the service instance name encodes IP+port and survives renames).
     */
    fun upsert(device: Device) {
        _devices.update { prev ->
            val idx = prev.indexOfFirst { it.id == device.id }
            if (idx < 0) prev + device else prev.toMutableList().also { it[idx] = device }
        }
    }

    fun removeById(id: String) {
        _devices.update { prev -> prev.filter { it.id != id } }
    }

    /**
     * Keys by [Device.name]. Use when name is the stable identity at upsert time
     * (e.g. NSD/Bonjour, where id encodes a transient IP). Preserves insertion order.
     */
    fun upsertByName(device: Device) {
        _devices.update { prev ->
            val idx = prev.indexOfFirst { it.name == device.name }
            if (idx < 0) prev + device else prev.toMutableList().also { it[idx] = device }
        }
    }

    fun removeByName(name: String) {
        _devices.update { prev -> prev.filter { it.name != name } }
    }

    fun clear() {
        _devices.update { emptyList() }
    }
}
