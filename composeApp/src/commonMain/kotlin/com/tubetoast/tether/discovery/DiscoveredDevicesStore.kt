package com.tubetoast.tether.discovery

import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.util.ScopedJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.wrapper.withTag
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private val log = KydraLog.withTag(default = "DiscoveredDevicesStore")

/**
 * Insertion-ordered store for discovered peers. Thread-safe — observers see a
 * consistent snapshot.
 *
 * A background janitor loop (started via [start]) idle-expires peers learned through
 * /hello or fallback channels that have not been contacted within [idleWindow].
 * The store is fully usable when un-started; idle-expiry simply does not run.
 */
class DiscoveredDevicesStore(
    private val idleWindow: Duration = 5.minutes,
    private val sweepInterval: Duration = 30.seconds,
    private val staleGrace: Duration = 10.seconds,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private data class State(
        val devices: List<Device>,
        val lastSeen: Map<String, TimeMark>,
        val mdnsPresent: Set<String>,
    )

    private val combinedState = MutableStateFlow(State(emptyList(), emptyMap(), emptySet()))

    @OptIn(ExperimentalForInheritanceCoroutinesApi::class)
    val devices: StateFlow<List<Device>> = object : StateFlow<List<Device>> {
        override val replayCache: List<List<Device>> get() = listOf(value)
        override val value: List<Device> get() = combinedState.value.devices

        override suspend fun collect(collector: FlowCollector<List<Device>>): Nothing {
            combinedState.map { it.devices }.distinctUntilChanged().collect(collector)
            error("StateFlow collection never completes")
        }
    }

    private val janitor = ScopedJob()

    fun upsert(device: Device) {
        val fp = device.fingerprint
        combinedState.update { prev ->
            val newDevices = updatedDeviceList(prev.devices, device)
            val newLastSeen = if (fp != null) prev.lastSeen + (fp to timeSource.markNow()) else prev.lastSeen
            prev.copy(devices = newDevices, lastSeen = newLastSeen)
        }
    }

    /**
     * mDNS-present peers are exempt from idle-expiry and are removed only via
     * serviceLost → [removeByName].
     */
    fun upsertMdns(device: Device) {
        val fp = device.fingerprint
        combinedState.update { prev ->
            val newDevices = updatedDeviceList(prev.devices, device)
            val newMdnsPresent = if (fp != null) prev.mdnsPresent + fp else prev.mdnsPresent
            prev.copy(devices = newDevices, mdnsPresent = newMdnsPresent)
        }
    }

    fun touch(fingerprint: String?) {
        if (fingerprint == null) return
        combinedState.update { prev ->
            prev.copy(lastSeen = prev.lastSeen + (fingerprint to timeSource.markNow()))
        }
    }

    fun nameFor(fingerprint: String): String? = combinedState.value.devices
        .firstOrNull { it.fingerprint == fingerprint }
        ?.name

    fun removeByFingerprint(fingerprint: String) {
        combinedState.update { prev ->
            prev.copy(
                devices = prev.devices.filter { it.fingerprint != fingerprint },
                lastSeen = prev.lastSeen - fingerprint,
                mdnsPresent = prev.mdnsPresent - fingerprint,
            )
        }
    }

    /**
     * Looks up the entry whose current name matches [name] and removes it.
     * mDNS backing is revoked unconditionally. The device and its lastSeen mark are
     * retained when the peer's last-seen age is within [staleGrace] — a fresh /hello
     * still vouches, so the peer transitions to idle-eligible rather than disappearing.
     */
    fun removeByName(name: String) {
        combinedState.update { prev ->
            val fp = prev.devices.firstOrNull { it.name == name }?.fingerprint
            if (fp == null) return@update prev
            val newMdnsPresent = prev.mdnsPresent - fp
            val mark = prev.lastSeen[fp]
            if (mark != null && mark.elapsedNow() < staleGrace) {
                return@update prev.copy(mdnsPresent = newMdnsPresent)
            }
            prev.copy(
                devices = prev.devices.filter { it.fingerprint != fp },
                lastSeen = prev.lastSeen - fp,
                mdnsPresent = newMdnsPresent,
            )
        }
    }

    fun clear() {
        combinedState.value = State(emptyList(), emptyMap(), emptySet())
    }

    fun start(scope: CoroutineScope) {
        janitor.start(scope) {
            while (isActive) {
                delay(sweepInterval)
                evictIdle()
            }
        }
    }

    fun stop() {
        janitor.stop()
    }

    internal fun evictIdle() {
        combinedState.update { prev ->
            val expired = prev.lastSeen.entries
                .filter { (fp, mark) -> mark.elapsedNow() >= idleWindow && fp !in prev.mdnsPresent }
                .map { it.key }
                .toSet()
            if (expired.isEmpty()) return@update prev
            val newState = prev.copy(
                devices = prev.devices.filter { it.fingerprint !in expired },
                lastSeen = prev.lastSeen - expired,
            )
            log.info { "idle-expired ${expired.size} peer(s): $expired" }
            newState
        }
    }

    private fun updatedDeviceList(devices: List<Device>, device: Device): List<Device> {
        val fp = device.fingerprint ?: return devices + device
        val idx = devices.indexOfFirst { it.fingerprint == fp }
        return if (idx >= 0) devices.toMutableList().also { it[idx] = device } else devices + device
    }
}
