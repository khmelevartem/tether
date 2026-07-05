package com.tubetoast.tether.discovery

import com.tubetoast.tether.network.FileClient
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.util.ScopedJob
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.debug
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.warn
import ru.pocketbyte.kydra.log.wrapper.withTag
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val log = KydraLog.withTag(default = "HealthMonitor")

/**
 * Reachability probe. While started, probes every peer in [store] and removes a peer after
 * [failureThreshold] consecutive failed `/health` checks. Callers gate `start`/`stop` by their own
 * lifecycle — UI visibility on app targets, always-on for the process's lifetime in the headless
 * CLI. The cadence adapts: [period]
 * between cycles while every peer is healthy, [fastPeriod] as soon as any peer is mid-failure-streak,
 * reverting to [period] once all are healthy again. A peer with a transfer in flight
 * ([activeTransfers]) is excluded from probing and resumes with a cleared failure count once the
 * transfer ends.
 */
class HealthMonitor(
    private val store: DiscoveredDevicesStore,
    private val fileClient: FileClient,
    private val activeTransfers: ActiveTransfers,
    private val period: Duration = DEFAULT_PERIOD,
    private val fastPeriod: Duration = DEFAULT_FAST_PERIOD,
    private val failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD,
    private val probeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scopedJob = ScopedJob()

    fun start(scope: CoroutineScope) {
        log.info { "started — period=$period, fastPeriod=$fastPeriod, failureThreshold=$failureThreshold" }
        scopedJob.start(scope) {
            val failureCounts = mutableMapOf<String, Int>()
            probeOnce(failureCounts)
            while (isActive) {
                delay(if (failureCounts.isEmpty()) period else fastPeriod)
                probeOnce(failureCounts)
            }
        }
    }

    fun stop() {
        scopedJob.stop()
        log.info { "stopped" }
    }

    private suspend fun probeOnce(failureCounts: MutableMap<String, Int>) {
        val excludedIds = activeTransfers.peers.value.mapTo(mutableSetOf()) { it.id }
        val candidates = store.devices.value.filter { device -> device.fingerprint?.let { it !in excludedIds } == true }
        if (candidates.isNotEmpty()) {
            log.debug { "probe cycle — candidates=${candidates.size}, excluded=${excludedIds.size}" }
        }
        pruneStaleCounters(candidates, failureCounts)

        val results = coroutineScope {
            val probes = candidates.map { device ->
                async(probeDispatcher) { device.fingerprint!! to fileClient.checkHealth(device) }
            }
            probes.awaitAll()
        }

        results.forEach { (fingerprint, reachable) -> applyProbeResult(fingerprint, reachable, failureCounts) }
    }

    private fun applyProbeResult(fingerprint: String, reachable: Boolean, failureCounts: MutableMap<String, Int>) {
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

    private fun pruneStaleCounters(candidates: List<Device>, failureCounts: MutableMap<String, Int>) {
        val live = candidates.mapNotNull { it.fingerprint }.toSet()
        failureCounts.keys.retainAll { it in live }
    }
}

private val DEFAULT_PERIOD: Duration = 7.seconds
private val DEFAULT_FAST_PERIOD: Duration = 2.seconds
private const val DEFAULT_FAILURE_THRESHOLD = 3
