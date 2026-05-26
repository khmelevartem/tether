package com.tubetoast.tether.transfer

sealed interface BatchProgress {
    val peer: PeerIdentity

    data class Sending(
        override val peer: PeerIdentity,
        val currentFile: String,
        val currentIndex: Int,
        val totalFiles: Int,
        val sentBytes: Long,
        val totalBytes: Long?,
        val bytesPerSec: Long?,
        val skippedCount: Int,
        val perFile: List<PerFileStatus>,
    ) : BatchProgress

    data class Reconnecting(
        override val peer: PeerIdentity,
        val remainingSeconds: Int,
        val snapshotBeforeDrop: Sending,
    ) : BatchProgress

    data class Completed(
        override val peer: PeerIdentity,
        val outcome: BatchOutcome,
        val perFile: List<PerFileStatus>,
    ) : BatchProgress
}
