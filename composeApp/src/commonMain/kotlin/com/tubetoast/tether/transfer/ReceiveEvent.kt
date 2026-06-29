package com.tubetoast.tether.transfer

sealed interface ReceiveEvent {
    data class Started(
        val currentFile: String,
        val totalFiles: Int,
    ) : ReceiveEvent

    data class Progress(
        val name: String,
        val receivedBytes: Long,
        val totalBytes: Long?,
    ) : ReceiveEvent

    data class FileCompleted(
        val name: String,
    ) : ReceiveEvent

    data class BatchCompleted(
        val received: Int,
        val total: Int,
        val partialReason: PartialOutcome? = null,
    ) : ReceiveEvent

    data class Failed(
        val file: String,
        val reason: FailureReason,
    ) : ReceiveEvent

    data class ConnectionLost(
        val receivedSoFar: Int,
    ) : ReceiveEvent

    data object ReceiverSuspended : ReceiveEvent
}
