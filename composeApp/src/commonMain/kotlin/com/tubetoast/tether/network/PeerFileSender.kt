package com.tubetoast.tether.network

import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.protocol.SendResult
import com.tubetoast.tether.transfer.FileSource
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.transfer.PeerUnreachableException
import com.tubetoast.tether.transfer.toPeerIdentity

/**
 * Concrete `sendOne` for [com.tubetoast.tether.transfer.BatchSender]: resolves a peer's current
 * address from discovery and streams one source over [FileClient]. Lives in the data layer so
 * BatchSender stays transport-agnostic.
 *
 * Resolves the address per call rather than capturing it, so a peer that re-announced on a new
 * port between batches is reached at its latest address. Throws [PeerUnreachableException] on any
 * failure ([FileClient.send] folds errors into [SendResult.Failure]).
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
                    fileName = source.name,
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
