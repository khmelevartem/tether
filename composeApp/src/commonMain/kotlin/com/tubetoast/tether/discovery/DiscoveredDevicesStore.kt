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
            prev.replaceMatchingFingerprint(device) ?: (prev + device)
        }
    }

    private fun List<Device>.replaceMatchingFingerprint(device: Device): List<Device>? {
        val fp = device.fingerprint ?: return null
        val idx = indexOfFirst { it.fingerprint == fp }
        return if (idx >= 0) toMutableList().also { it[idx] = device } else null
    }

    /** Returns true when a peer with this [fingerprint] is already in the store. */
    fun knows(fingerprint: String): Boolean = _devices.value.any { it.fingerprint == fingerprint }

    fun removeByFingerprint(fingerprint: String) {
        _devices.update { prev -> prev.filter { it.fingerprint != fingerprint } }
    }

    /**
     * Looks up the entry whose current name matches [name] and removes it by fingerprint —
     * one atomic CAS step. A removal under a stale intermediate rename name (already
     * superseded by Rule 1) finds no match and is a no-op; the live canonical entry survives.
     */
    fun removeByName(name: String) {
        _devices.update { prev ->
            val fp = prev.firstOrNull { it.name == name }?.fingerprint
            if (fp == null) prev else prev.filter { it.fingerprint != fp }
        }
    }

    fun clear() {
        _devices.update { emptyList() }
    }
}
