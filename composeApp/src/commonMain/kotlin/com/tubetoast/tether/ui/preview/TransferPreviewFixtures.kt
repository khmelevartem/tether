package com.tubetoast.tether.ui.preview

import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.Direction
import com.tubetoast.tether.transfer.FailureReason
import com.tubetoast.tether.transfer.PartialOutcome
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.transfer.PeerTransferState
import com.tubetoast.tether.transfer.PerFileStatus
import com.tubetoast.tether.transfer.TransferErrorReason

object TransferPreviewFixtures {
    val peer = PeerIdentity("Alice")
    val device: Device = PreviewFixtures.singleDevice.first()

    val idleCollapsed = PeerTransferState.Idle(peer)
    val idleExpanded = PeerTransferState.Idle(peer)

    val activeOutboundClaimed = PeerTransferState.ActiveOutbound.Claimed(
        peer = peer,
        totalFiles = 3,
        totalBytes = 31_457_280L,
        perFile = listOf(
            PerFileStatus.Queued("photo_001.jpg", 10_485_760L),
            PerFileStatus.Queued("photo_002.jpg", 10_485_760L),
            PerFileStatus.Queued("photo_003.jpg", 10_485_760L),
        ),
    )

    val activeOutbound = PeerTransferState.ActiveOutbound.Sending(
        peer = peer,
        currentFile = "vacation_photo_2024_summer_beach_holiday.jpg",
        currentIndex = 2,
        totalFiles = 10,
        sentBytes = 52_428_800L,
        totalBytes = 104_857_600L,
        bytesPerSec = 5_242_880L,
        skippedCount = 0,
        perFile = listOf(
            PerFileStatus.Done("photo_001.jpg", 10_485_760L),
            PerFileStatus.Done("photo_002.jpg", 10_485_760L),
            PerFileStatus.InProgress("vacation_photo_2024_summer_beach_holiday.jpg", 10_485_760L, 5_242_880L),
            PerFileStatus.Queued("photo_004.jpg", 10_485_760L),
        ),
    )

    val activeOutboundWithSkips = activeOutbound.copy(
        skippedCount = 2,
        perFile = activeOutbound.perFile.toMutableList().also {
            it[1] =
                PerFileStatus.Failed(
                    "photo_002.jpg",
                    10_485_760L,
                    FailureReason.CancelledByUser,
                    cancelledByUser = true,
                )
        },
    )

    val activeOutboundCalculating = PeerTransferState.ActiveOutbound.Sending(
        peer = peer,
        currentFile = "document.pdf",
        currentIndex = 0,
        totalFiles = 3,
        sentBytes = 1_048_576L,
        totalBytes = null,
        bytesPerSec = null,
        skippedCount = 0,
        perFile = listOf(
            PerFileStatus.InProgress("document.pdf", null, 1_048_576L),
            PerFileStatus.Queued("spreadsheet.xlsx", null),
            PerFileStatus.Queued("presentation.pptx", null),
        ),
    )

    val activeInbound = PeerTransferState.ActiveInbound(
        peer = peer,
        currentFile = "shared_document_from_alice.docx",
        currentIndex = 1,
        totalFiles = 5,
        receivedBytes = 20_971_520L,
        totalBytes = 52_428_800L,
        bytesPerSec = 3_145_728L,
        perFile = listOf(
            PerFileStatus.Done("file_one.jpg", 10_485_760L),
            PerFileStatus.InProgress("shared_document_from_alice.docx", 10_485_760L, 5_242_880L),
            PerFileStatus.Queued("file_three.pdf", 10_485_760L),
        ),
    )

    val reconnecting = PeerTransferState.Reconnecting(
        peer = peer,
        direction = Direction.Outbound,
        remainingSeconds = 12,
        snapshotBeforeDrop = activeOutbound,
    )

    val sentFull = PeerTransferState.Sent(
        peer = peer,
        sent = 10,
        total = 10,
        perFile = emptyList(),
        partialReason = null,
    )

    val sentPartialReceiverCancelled = PeerTransferState.Sent(
        peer = peer,
        sent = 4,
        total = 10,
        perFile = emptyList(),
        partialReason = PartialOutcome.ReceiverCancelled,
    )

    val sentPartialConnectionLost = PeerTransferState.Sent(
        peer = peer,
        sent = 6,
        total = 10,
        perFile = emptyList(),
        partialReason = PartialOutcome.ConnectionLost,
    )

    val sentPartialUnreadable = PeerTransferState.Sent(
        peer = peer,
        sent = 8,
        total = 10,
        perFile = emptyList(),
        partialReason = PartialOutcome.FilesUnreadable(2),
    )

    val receivedFull = PeerTransferState.Received(
        peer = peer,
        received = 5,
        total = 5,
        perFile = emptyList(),
        partialReason = null,
    )

    val receivedPartialSenderCancelled = PeerTransferState.Received(
        peer = peer,
        received = 3,
        total = 5,
        perFile = emptyList(),
        partialReason = PartialOutcome.SenderCancelled,
    )

    val receivedPartialReceiverCancelled = PeerTransferState.Received(
        peer = peer,
        received = 2,
        total = 5,
        perFile = emptyList(),
        partialReason = PartialOutcome.ReceiverCancelled,
    )

    val receivedPartialConnectionLost = PeerTransferState.Received(
        peer = peer,
        received = 3,
        total = 5,
        perFile = emptyList(),
        partialReason = PartialOutcome.ConnectionLost,
    )

    val cancelledClean = PeerTransferState.Cancelled(
        peer = peer,
        sent = 0,
        remaining = emptyList(),
        perFile = emptyList(),
    )

    val cancelledPartial = PeerTransferState.Cancelled(
        peer = peer,
        sent = 4,
        remaining = listOf("file_05.txt", "file_06.txt", "file_07.txt"),
        perFile = emptyList(),
    )

    val errorNetworkLost = PeerTransferState.Error(
        peer = peer,
        reason = TransferErrorReason.NetworkLost,
        sent = 2,
        perFile = emptyList(),
    )

    val errorPeerUnreachable = PeerTransferState.Error(
        peer = peer,
        reason = TransferErrorReason.PeerUnreachable,
        sent = 3,
        perFile = emptyList(),
    )

    val errorReceiverWriteFailed = PeerTransferState.Error(
        peer = peer,
        reason = TransferErrorReason.ReceiverWriteFailed,
        sent = 1,
        perFile = emptyList(),
    )

    val errorAllFilesFailed = PeerTransferState.Error(
        peer = peer,
        reason = TransferErrorReason.AllFilesFailed,
        sent = 0,
        perFile = emptyList(),
    )

    val errorReceiverSuspended = PeerTransferState.Error(
        peer = peer,
        reason = TransferErrorReason.ReceiverSuspended,
        sent = 0,
        perFile = emptyList(),
    )

    val perFileQueued = PerFileStatus.Queued("document.pdf", 2_097_152L)
    val perFileInProgress = PerFileStatus.InProgress("photo_large.jpg", 10_485_760L, 3_145_728L)
    val perFileDone = PerFileStatus.Done("report.docx", 524_288L)
    val perFileFailedRetryable = PerFileStatus.Failed("presentation.pptx", 5_242_880L, FailureReason.PeerUnreachable)
    val perFileFailedOffline = PerFileStatus.Failed("archive.zip", 104_857_600L, FailureReason.PeerUnreachable)
    val perFileFailedCancelledByUser = PerFileStatus.Failed(
        "notes.txt",
        10_240L,
        FailureReason.CancelledByUser,
        cancelledByUser = true,
    )
    val perFileFailedTransferCancelled = PerFileStatus.Failed(
        "backup.tar",
        52_428_800L,
        FailureReason.TransferCancelled,
    )

    val perFileListInProgress = listOf(
        PerFileStatus.Done("photo_001.jpg", 10_485_760L),
        PerFileStatus.InProgress("vacation_photo_2024_summer_beach_holiday.jpg", 10_485_760L, 5_242_880L),
        PerFileStatus.Queued("document.pdf", 2_097_152L),
        PerFileStatus.Queued("spreadsheet.xlsx", 1_048_576L),
    )

    val perFileListPartial = listOf(
        PerFileStatus.Done("photo_001.jpg", 10_485_760L),
        PerFileStatus.Done("photo_002.jpg", 10_485_760L),
        PerFileStatus.Failed("photo_003.jpg", 10_485_760L, FailureReason.PeerUnreachable),
        PerFileStatus.Failed("document.pdf", 2_097_152L, FailureReason.CancelledByUser, cancelledByUser = true),
    )

    val perFileListAllFailed = listOf(
        PerFileStatus.Failed("photo_001.jpg", 10_485_760L, FailureReason.NetworkLost),
        PerFileStatus.Failed("document.pdf", 2_097_152L, FailureReason.NetworkLost),
        PerFileStatus.Failed("spreadsheet.xlsx", 1_048_576L, FailureReason.NetworkLost),
    )

    val activeOutboundWithPerFile = activeOutbound.copy(perFile = perFileListInProgress)

    val sentPartialWithPerFile = PeerTransferState.Sent(
        peer = peer,
        sent = 2,
        total = 4,
        perFile = perFileListPartial,
        partialReason = PartialOutcome.ConnectionLost,
    )

    val errorWithPerFile = PeerTransferState.Error(
        peer = peer,
        reason = TransferErrorReason.AllFilesFailed,
        sent = 0,
        perFile = perFileListAllFailed,
    )
}
