package com.tubetoast.tether.transfer

sealed interface PeerTransferState {
    val peer: PeerIdentity

    data class Idle(
        override val peer: PeerIdentity,
    ) : PeerTransferState

    sealed class ActiveOutbound : PeerTransferState {
        abstract override val peer: PeerIdentity
        abstract val totalFiles: Int
        abstract val totalBytes: Long?
        abstract val perFile: List<PerFileStatus>

        /** Engine atomically claimed via compareAndSet; the BatchSender has not produced its first
         * progress heartbeat yet. No per-byte progress fields exist at this phase. */
        data class Claimed(
            override val peer: PeerIdentity,
            override val totalFiles: Int,
            override val totalBytes: Long?,
            override val perFile: List<PerFileStatus>,
        ) : ActiveOutbound()

        data class Sending(
            override val peer: PeerIdentity,
            val currentFile: String,
            val currentIndex: Int,
            override val totalFiles: Int,
            val sentBytes: Long,
            override val totalBytes: Long?,
            val bytesPerSec: Long?,
            val skippedCount: Int,
            override val perFile: List<PerFileStatus>,
        ) : ActiveOutbound()
    }

    data class ActiveInbound(
        override val peer: PeerIdentity,
        val currentFile: String,
        val currentIndex: Int,
        val totalFiles: Int,
        val receivedBytes: Long,
        val totalBytes: Long?,
        val bytesPerSec: Long?,
        val perFile: List<PerFileStatus>,
    ) : PeerTransferState

    data class Reconnecting(
        override val peer: PeerIdentity,
        val direction: Direction,
        val remainingSeconds: Int,
        val snapshotBeforeDrop: PeerTransferState,
    ) : PeerTransferState

    data class Sent(
        override val peer: PeerIdentity,
        val sent: Int,
        val total: Int,
        val perFile: List<PerFileStatus>,
        val partialReason: PartialOutcome?,
    ) : PeerTransferState

    data class Received(
        override val peer: PeerIdentity,
        val received: Int,
        val total: Int,
        val perFile: List<PerFileStatus>,
        val partialReason: PartialOutcome?,
    ) : PeerTransferState

    data class Cancelled(
        override val peer: PeerIdentity,
        val sent: Int,
        val remaining: List<String>,
        val perFile: List<PerFileStatus>,
    ) : PeerTransferState

    data class Error(
        override val peer: PeerIdentity,
        val reason: TransferErrorReason,
        val sent: Int,
        val perFile: List<PerFileStatus>,
    ) : PeerTransferState
}
