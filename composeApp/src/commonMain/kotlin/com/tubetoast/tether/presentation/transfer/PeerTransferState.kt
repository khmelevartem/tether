package com.tubetoast.tether.presentation.transfer

import com.tubetoast.tether.transfer.PartialOutcome
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.transfer.PerFileStatus
import com.tubetoast.tether.transfer.TransferErrorReason

sealed interface PeerTransferState {
    val peer: PeerIdentity

    data class Idle(
        override val peer: PeerIdentity,
        val expanded: Boolean = false,
    ) : PeerTransferState

    data class ActiveOutbound(
        override val peer: PeerIdentity,
        val currentFile: String,
        val currentIndex: Int,
        val totalFiles: Int,
        val sentBytes: Long,
        val totalBytes: Long?,
        val bytesPerSec: Long?,
        val skippedCount: Int,
        val perFile: List<PerFileStatus>,
    ) : PeerTransferState

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
