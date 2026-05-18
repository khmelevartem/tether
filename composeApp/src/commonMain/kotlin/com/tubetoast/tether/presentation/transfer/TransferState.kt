package com.tubetoast.tether.presentation.transfer

import com.tubetoast.tether.protocol.Device

sealed class TransferState {
    data object Preparing : TransferState()

    data class FolderConfirm(
        val fileCount: Int,
        val totalBytes: Long,
    ) : TransferState()

    data class InProgress(
        val currentFile: String,
        val bytesDone: Long,
        val bytesTotal: Long?,
        val speedBytesPerSec: Long,
        val inlineNotice: String? = null,
    ) : TransferState()

    data class CancelConfirm(
        val snapshot: InProgress,
    ) : TransferState()

    sealed class Terminal : TransferState() {
        data class AllSuccess(
            val peer: Device,
            val count: Int,
        ) : Terminal()

        data class PartialFailure(
            val peer: Device,
            val sent: Int,
            val total: Int,
            val failed: List<FailedFile>,
        ) : Terminal()

        data class AllFailed(
            val peer: Device,
            val failed: List<FailedFile>,
        ) : Terminal()

        data object Cancelled : Terminal()

        data class ConnectionErrorSummary(
            val peer: Device,
            val sent: Int,
            val total: Int,
            val failed: List<FailedFile>,
        ) : Terminal()
    }
}
