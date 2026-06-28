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
     * Emitted when the upload connection for this peer is gone — either a genuine network drop
     * ([cancelled] = false) or a deliberate receiver-side cancel ([cancelled] = true).
     * The router uses [cancelled] to decide whether to synthesize [ReceiveEvent.BatchCompleted]
     * (cancel → clean batch end) or emit only [ReceiveEvent.ConnectionLost] (drop → Reconnecting).
     */
    data class ConnectionLost(
        override val peer: PeerIdentity,
        val receivedSoFar: Int,
        val cancelled: Boolean = false,
    ) : InboundEvent
}
