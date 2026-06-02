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
     * Inserts or replaces using fingerprint-first deduplication:
     * 1. Same fingerprint (non-null) → replace in place; latest name/host/port wins.
     * 2. Incoming has fingerprint, existing at same host:port has none → promote (replace).
     * 3. Incoming has no fingerprint and existing entry at same host:port exists → drop incoming.
     * 4. Fallback: same id replaces; same name with different id evicts stale entry.
     */
    fun upsert(device: Device) {
        _devices.update { prev ->
            if (device.fingerprint != null) {
                val fpIdx = prev.indexOfFirst { it.fingerprint == device.fingerprint }
                if (fpIdx >= 0) {
                    return@update prev.toMutableList().also { it[fpIdx] = device }
                }
            }

            if (device.fingerprint != null) {
                val hostPortIdx = prev.indexOfFirst {
                    it.host == device.host &&
                        it.port == device.port &&
                        it.fingerprint == null
                }
                if (hostPortIdx >= 0) {
                    return@update prev.toMutableList().also { it[hostPortIdx] = device }
                }
            }

            if (device.fingerprint == null) {
                val hostPortMatch = prev.any { it.host == device.host && it.port == device.port }
                if (hostPortMatch) return@update prev
            }

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

    fun removeByFingerprint(fingerprint: String) {
        _devices.update { prev -> prev.filter { it.fingerprint != fingerprint } }
    }

    fun clear() {
        _devices.update { emptyList() }
    }
}
