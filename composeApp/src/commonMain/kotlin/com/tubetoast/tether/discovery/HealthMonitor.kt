package com.tubetoast.tether.discovery

import com.tubetoast.tether.network.FileClient
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.util.ScopedJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.warn
import ru.pocketbyte.kydra.log.wrapper.withTag
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val log = KydraLog.withTag(default = "HealthMonitor")

/**
 * Foreground-only reachability probe. While started, probes every peer in [store] on a fixed
 * period and removes a peer after [failureThreshold] consecutive failed `/health` checks. A peer
 * with a transfer in flight ([activeTransfers]) is excluded from probing and resumes with a
 * cleared failure count once the transfer ends.
 */
class HealthMonitor(
    private val store: DiscoveredDevicesStore,
    private val fileClient: FileClient,
    private val activeTransfers: ActiveTransfers,
    private val period: Duration = DEFAULT_PERIOD,
    private val failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD,
) {
    private val scopedJob = ScopedJob()
    private val failureCounts = mutableMapOf<String, Int>()

    @Volatile private var runningScope: CoroutineScope? = null

    fun start(scope: CoroutineScope) {
        runningScope = scope
        log.info { "started — period=$period, failureThreshold=$failureThreshold" }
        scopedJob.start(scope) {
            probeOnce()
            while (isActive) {
                delay(period)
                probeOnce()
            }
        }
    }

    fun stop() {
        scopedJob.stop()
        runningScope = null
        failureCounts.clear()
        log.info { "stopped" }
    }

    fun refreshNow() {
        runningScope?.launch { probeOnce() }
    }

    private suspend fun probeOnce() {
        val excluded = activeTransfers.peers.value
        val candidates = store.devices.value.filter { device -> device.fingerprint?.let { it !in excluded } == true }
        pruneStaleCounters(candidates, excluded)

        val results = coroutineScope {
            val probes = candidates.map { device ->
                async { device.fingerprint!! to fileClient.checkHealth(device) }
            }
            probes.awaitAll()
        }

        results.forEach { (fingerprint, reachable) -> applyProbeResult(fingerprint, reachable) }
    }

    private fun applyProbeResult(fingerprint: String, reachable: Boolean) {
        if (reachable) {
            failureCounts.remove(fingerprint)
            return
        }
        val failures = (failureCounts[fingerprint] ?: 0) + 1
        if (failures >= failureThreshold) {
            failureCounts.remove(fingerprint)
            store.removeByFingerprint(fingerprint)
            log.warn { "peer unreachable after $failures probes → $fingerprint" }
        } else {
            failureCounts[fingerprint] = failures
        }
    }

    private fun pruneStaleCounters(candidates: List<Device>, excluded: Set<String>) {
        val live = candidates.mapNotNull { it.fingerprint }.toSet()
        failureCounts.keys.retainAll { it in live || it in excluded }
    }
}

private val DEFAULT_PERIOD: Duration = 7.seconds
private const val DEFAULT_FAILURE_THRESHOLD = 3
