package com.tubetoast.tether.transfer

sealed interface PeerTransferState {
    data object Idle : PeerTransferState

    sealed class ActiveOutbound : PeerTransferState {
        abstract val totalFiles: Int
        abstract val totalBytes: Long?
        abstract val perFile: List<PerFileStatus>

        /** Outbound transfer reserved by the engine; per-byte progress is not yet available. */
        data class Claimed(
            override val totalFiles: Int,
            override val totalBytes: Long?,
            override val perFile: List<PerFileStatus>,
        ) : ActiveOutbound()

        /** The current file is still materializing (e.g. exporting a photo); no bytes flow yet, so no rate exists. */
        data class Preparing(
            val currentFile: String,
            val currentIndex: Int,
            override val totalFiles: Int,
            val sentBytes: Long,
            override val totalBytes: Long?,
            val skippedCount: Int,
            override val perFile: List<PerFileStatus>,
        ) : ActiveOutbound()

        data class Sending(
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
        val currentFile: String,
        val currentIndex: Int,
        val totalFiles: Int,
        val receivedBytes: Long,
        val totalBytes: Long?,
        val bytesPerSec: Long?,
        val perFile: List<PerFileStatus>,
    ) : PeerTransferState

    data class Reconnecting(
        val direction: Direction,
        val remainingSeconds: Int,
        val snapshotBeforeDrop: PeerTransferState,
    ) : PeerTransferState

    data class Sent(
        val sent: Int,
        val total: Int,
        val perFile: List<PerFileStatus>,
        val partialReason: PartialOutcome?,
    ) : PeerTransferState

    data class Received(
        val received: Int,
        val total: Int,
        val perFile: List<PerFileStatus>,
        val partialReason: PartialOutcome?,
    ) : PeerTransferState

    data class Cancelled(
        val sent: Int,
        val remaining: List<String>,
        val perFile: List<PerFileStatus>,
    ) : PeerTransferState

    data class Error(
        val reason: TransferErrorReason,
        val sent: Int,
        val perFile: List<PerFileStatus>,
    ) : PeerTransferState
}
