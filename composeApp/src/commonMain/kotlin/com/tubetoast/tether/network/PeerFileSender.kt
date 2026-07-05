package com.tubetoast.tether.network

import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.protocol.PeerIdentity
import com.tubetoast.tether.protocol.SendResult
import com.tubetoast.tether.transfer.FileSource
import com.tubetoast.tether.transfer.PeerUnreachableException
import com.tubetoast.tether.transfer.toPeerIdentity

/**
 * Resolves the peer's current discovered entry per call (not a captured snapshot) and streams
 * one source over [FileClient]. Throws [PeerUnreachableException] when the peer is no longer
 * in the store or when the send fails ([FileClient.send] folds errors into [SendResult.Failure]).
 */
class PeerFileSender(
    private val fileClient: FileClient,
    private val discoveredDevicesStore: DiscoveredDevicesStore,
) {
    suspend fun send(
        peer: PeerIdentity,
        source: FileSource,
        onProgress: (bytesTransferred: Long, totalBytes: Long?) -> Unit,
    ) {
        val device = discoveredDevicesStore.devices.value
            .firstOrNull { it.toPeerIdentity() == peer }
            ?: throw PeerUnreachableException()
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
}
