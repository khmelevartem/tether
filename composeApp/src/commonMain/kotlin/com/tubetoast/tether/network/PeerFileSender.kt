package com.tubetoast.tether.network

import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.protocol.PeerIdentity
import com.tubetoast.tether.protocol.SendResult
import com.tubetoast.tether.transfer.FileSource
import com.tubetoast.tether.transfer.PeerUnreachableException
import com.tubetoast.tether.transfer.toPeerIdentity
import kotlinx.coroutines.delay
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.warn
import ru.pocketbyte.kydra.log.wrapper.withTag
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val log = KydraLog.withTag(default = "PeerFileSender")

/** Delay before each retry after the first attempt fails; total budget stays under ~10s. */
private val END_BATCH_RETRY_DELAYS: List<Duration> =
    listOf(500.milliseconds, 1_000.milliseconds, 2_000.milliseconds, 4_000.milliseconds)

/**
 * Resolves the peer's current discovered entry per call (not a captured snapshot) and streams
 * one source over [FileClient]. Throws [PeerUnreachableException] when the peer is no longer
 * in the store or when the send fails ([FileClient.send] folds errors into [SendResult.Failure]).
 */
class PeerFileSender(
    private val fileClient: FileClient,
    private val discoveredDevicesStore: DiscoveredDevicesStore,
) {
    suspend fun beginBatch(peer: PeerIdentity, batchId: String, totalFiles: Int, totalBytes: Long?) {
        val device = resolveDevice(peer) ?: throw PeerUnreachableException()
        if (!fileClient.beginBatch(device, batchId, totalFiles, totalBytes)) throw PeerUnreachableException()
    }

    /** Best-effort: swallows [PeerUnreachableException] — if the peer is gone the receiver times out on its own. */
    suspend fun cancelBatch(peer: PeerIdentity, batchId: String) {
        val device = resolveDevice(peer) ?: return
        fileClient.cancelBatch(device, batchId)
    }

    /**
     * Retries a lost `/batch-end` — the sole signal that lets the receiver finalize a batch delivered
     * short of its declared total. Files are already delivered by the time this runs, so exhausting
     * retries only loses the receiver's completion marker; it never fails the sender's own outcome.
     */
    suspend fun endBatch(peer: PeerIdentity, batchId: String) {
        val device = resolveDevice(peer) ?: return
        if (fileClient.endBatch(device, batchId)) return
        for (delayBeforeRetry in END_BATCH_RETRY_DELAYS) {
            delay(delayBeforeRetry)
            if (fileClient.endBatch(device, batchId)) return
        }
        log.warn { "batch-end exhausted retries → ${device.host}:${device.port} id=$batchId" }
    }

    suspend fun send(
        peer: PeerIdentity,
        source: FileSource,
        onProgress: (bytesTransferred: Long, totalBytes: Long?) -> Unit,
    ) {
        val device = resolveDevice(peer) ?: throw PeerUnreachableException()
        try {
            when (
                val result = fileClient.send(
                    device = device,
                    channel = source.openReadChannel(),
                    // relativePath (not name) so folder sends preserve nesting; the receiver
                    // sanitizes the wire `name` param as a relative path. Equals name for flat sends.
                    fileName = source.relativePath,
                    totalBytes = source.sizeBytes,
                    onProgress = onProgress,
                )
            ) {
                is SendResult.Success -> Unit
                is SendResult.Failure -> throw PeerUnreachableException(RuntimeException(result.reason))
            }
        } finally {
            source.close()
        }
    }

    private fun resolveDevice(peer: PeerIdentity) =
        discoveredDevicesStore.devices.value.firstOrNull { it.toPeerIdentity() == peer }
}
