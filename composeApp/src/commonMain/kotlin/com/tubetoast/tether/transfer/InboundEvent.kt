package com.tubetoast.tether.transfer

sealed interface InboundEvent {
    val peer: PeerIdentity

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

    /**
     * Emitted when a mid-stream client disconnect is detected for the peer's upload connection.
     * The transfer layer interprets this as the batch-end signal when reconnection does not occur
     * within the reconnection timeout.
     */
    data class ConnectionLost(
        override val peer: PeerIdentity,
        val receivedSoFar: Int,
    ) : InboundEvent
}
