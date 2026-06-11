package com.tubetoast.tether.transfer

sealed interface BatchProgress {
    data class Sending(
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
        val remainingSeconds: Int,
        val snapshotBeforeDrop: Sending,
    ) : BatchProgress

    data class Completed(
        val outcome: BatchOutcome,
        val perFile: List<PerFileStatus>,
    ) : BatchProgress
}
