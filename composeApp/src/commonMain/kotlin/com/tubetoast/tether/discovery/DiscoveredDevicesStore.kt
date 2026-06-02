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

    fun upsert(device: Device) {
        _devices.update { prev ->
            prev.replaceMatchingFingerprint(device)
                ?: prev.promoteNameOnlyAtSameAddress(device)
                ?: prev.keepExistingIfAddressCovered(device)
                ?: (prev + device)
        }
    }

    private fun List<Device>.replaceMatchingFingerprint(device: Device): List<Device>? {
        val fp = device.fingerprint ?: return null
        val idx = indexOfFirst { it.fingerprint == fp }
        return if (idx >= 0) toMutableList().also { it[idx] = device } else null
    }

    private fun List<Device>.promoteNameOnlyAtSameAddress(device: Device): List<Device>? {
        if (device.fingerprint == null) return null
        val idx = indexOfFirst { it.host == device.host && it.port == device.port && it.fingerprint == null }
        return if (idx >= 0) toMutableList().also { it[idx] = device } else null
    }

    private fun List<Device>.keepExistingIfAddressCovered(device: Device): List<Device>? {
        if (device.fingerprint != null) return null
        return if (any { it.host == device.host && it.port == device.port }) this else null
    }

    fun removeByName(name: String) {
        _devices.update { prev -> prev.filter { it.name != name } }
    }

    fun removeByFingerprint(fingerprint: String) {
        _devices.update { prev -> prev.filter { it.fingerprint != fingerprint } }
    }

    fun clear() {
        _devices.update { emptyList() }
    }
}
