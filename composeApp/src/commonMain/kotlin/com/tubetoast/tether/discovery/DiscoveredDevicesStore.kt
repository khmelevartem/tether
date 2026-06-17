package com.tubetoast.tether.discovery

import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.wrapper.withTag
import kotlin.concurrent.Volatile
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
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    // CAS-based map update mirrors _devices pattern — synchronized is unavailable in commonMain
    // and Mutex cannot guard non-suspend callers (mDNS callbacks).
    private val lastSeenMarks = MutableStateFlow<Map<String, TimeMark>>(emptyMap())

    @Volatile private var janitorJob: Job? = null

    fun upsert(device: Device) {
        val fp = device.fingerprint
        if (fp != null) stampNow(fp)
        _devices.update { prev ->
            prev.replaceMatchingFingerprint(device) ?: (prev + device)
        }
    }

    /** No-op when [fingerprint] is null — entries without a fingerprint are not tracked. */
    fun touch(fingerprint: String?) {
        if (fingerprint == null) return
        lastSeenMarks.update { prev ->
            if (prev.containsKey(fingerprint)) prev + (fingerprint to timeSource.markNow()) else prev
        }
    }

    fun nameFor(fingerprint: String): String? = _devices.value.firstOrNull { it.fingerprint == fingerprint }?.name

    fun removeByFingerprint(fingerprint: String) {
        lastSeenMarks.update { it - fingerprint }
        _devices.update { prev -> prev.filter { it.fingerprint != fingerprint } }
    }

    /**
     * Looks up the entry whose current name matches [name] and removes it by fingerprint —
     * one atomic step. Skipped if the peer's last-seen age is within [staleGrace], meaning a
     * fresh /hello upsert or active-reuse touch has superseded this serviceLost notification.
     */
    fun removeByName(name: String) {
        _devices.update { prev ->
            val fp = prev.firstOrNull { it.name == name }?.fingerprint
            if (fp == null) return@update prev
            val mark = lastSeenMarks.value[fp]
            if (mark != null && mark.elapsedNow() < staleGrace) return@update prev
            lastSeenMarks.update { it - fp }
            prev.filter { it.fingerprint != fp }
        }
    }

    fun clear() {
        lastSeenMarks.value = emptyMap()
        _devices.update { emptyList() }
    }

    fun start(scope: CoroutineScope) {
        if (janitorJob != null) return
        janitorJob = scope.launch {
            while (isActive) {
                delay(sweepInterval)
                evictIdle()
            }
        }
    }

    fun stop() {
        janitorJob?.cancel()
        janitorJob = null
    }

    internal fun evictIdle() {
        val snapshot = lastSeenMarks.value
        val expired = snapshot.entries
            .filter { (_, mark) -> mark.elapsedNow() >= idleWindow }
            .map { it.key }
            .toSet()
        if (expired.isEmpty()) return
        lastSeenMarks.update { prev -> prev - expired }
        _devices.update { prev -> prev.filter { it.fingerprint !in expired } }
        log.info { "idle-expired ${expired.size} peer(s): $expired" }
    }

    private fun stampNow(fingerprint: String) {
        lastSeenMarks.update { prev -> prev + (fingerprint to timeSource.markNow()) }
    }

    private fun List<Device>.replaceMatchingFingerprint(device: Device): List<Device>? {
        val fp = device.fingerprint ?: return null
        val idx = indexOfFirst { it.fingerprint == fp }
        return if (idx >= 0) toMutableList().also { it[idx] = device } else null
    }
}
