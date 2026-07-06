package com.tubetoast.tether.transfer

import com.tubetoast.tether.protocol.PeerIdentity

sealed interface InboundEvent {
    val peer: PeerIdentity

    data class BatchStarted(
        override val peer: PeerIdentity,
        val batchId: String,
        val totalFiles: Int,
        val totalBytes: Long?,
    ) : InboundEvent

    data class FileStarted(
        override val peer: PeerIdentity,
        val name: String,
    ) : InboundEvent

    data class Progress(
        override val peer: PeerIdentity,
        val name: String,
        val receivedBytes: Long,
        val totalBytes: Long?,
    ) : InboundEvent

    data class FileCompleted(
        override val peer: PeerIdentity,
        val name: String,
        val finalPath: String?,
    ) : InboundEvent

    data class Failed(
        override val peer: PeerIdentity,
        val name: String,
        val reason: FailureReason,
    ) : InboundEvent

    /** Emitted when the upload connection for this peer is gone because the network dropped. */
    data class ConnectionLost(
        override val peer: PeerIdentity,
    ) : InboundEvent

    /** Emitted when the receiver deliberately cancels an in-flight upload. */
    data class CancelledByReceiver(
        override val peer: PeerIdentity,
    ) : InboundEvent

    /** Emitted when the sender signals a deliberate batch cancel via /batch-cancel. */
    data class BatchCancelled(
        override val peer: PeerIdentity,
        val batchId: String,
    ) : InboundEvent

    /**
     * Emitted when the sender signals it has finished walking the batch via /batch-end. Finalizes
     * a batch that delivered fewer files than declared (sender-side skips) so the receiver reaches
     * a terminal state instead of waiting forever for the file-completion count to reach the total.
     */
    data class BatchEnd(
        override val peer: PeerIdentity,
        val batchId: String,
    ) : InboundEvent
}
