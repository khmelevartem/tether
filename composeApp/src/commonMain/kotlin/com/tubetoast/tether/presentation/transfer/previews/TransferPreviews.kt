package com.tubetoast.tether.presentation.transfer.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.tubetoast.tether.presentation.transfer.FailedFile
import com.tubetoast.tether.presentation.transfer.FailureReason
import com.tubetoast.tether.presentation.transfer.TransferProgressScreen
import com.tubetoast.tether.presentation.transfer.TransferState
import com.tubetoast.tether.presentation.transfer.TransferSummaryScreen
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.ui.theme.TetherTheme

private val fakePeer = Device("MacBook Pro", "192.168.1.100", 8080)

@Preview
@Composable
private fun PreviewPreparingLight() = TetherTheme(darkTheme = false) {
    TransferProgressScreen(
        state = TransferState.Preparing,
        peerName = fakePeer.name,
        onCancel = {},
        onBack = {},
        onFolderConfirm = {},
        onRetryAll = {},
    )
}

@Preview
@Composable
private fun PreviewPreparingDark() = TetherTheme(darkTheme = true) {
    TransferProgressScreen(
        state = TransferState.Preparing,
        peerName = fakePeer.name,
        onCancel = {},
        onBack = {},
        onFolderConfirm = {},
        onRetryAll = {},
    )
}

@Preview
@Composable
private fun PreviewInProgressLight() = TetherTheme(darkTheme = false) {
    TransferProgressScreen(
        state = TransferState.InProgress(
            currentFile = "Vacation photos IMG_4721.jpg",
            bytesDone = 12_800_000L,
            bytesTotal = 48_700_000L,
            speedBytesPerSec = 2_100_000L,
        ),
        peerName = fakePeer.name,
        onCancel = {},
        onBack = {},
        onFolderConfirm = {},
        onRetryAll = {},
    )
}

@Preview
@Composable
private fun PreviewInProgressDark() = TetherTheme(darkTheme = true) {
    TransferProgressScreen(
        state = TransferState.InProgress(
            currentFile = "Vacation photos IMG_4721.jpg",
            bytesDone = 12_800_000L,
            bytesTotal = 48_700_000L,
            speedBytesPerSec = 2_100_000L,
        ),
        peerName = fakePeer.name,
        onCancel = {},
        onBack = {},
        onFolderConfirm = {},
        onRetryAll = {},
    )
}

@Preview
@Composable
private fun PreviewInProgressWithNoticeLight() = TetherTheme(darkTheme = false) {
    TransferProgressScreen(
        state = TransferState.InProgress(
            currentFile = "photo.jpg",
            bytesDone = 5_000_000L,
            bytesTotal = 20_000_000L,
            speedBytesPerSec = 1_500_000L,
            inlineNotice = "Couldn't read bad_file.mov — skipping.",
        ),
        peerName = fakePeer.name,
        onCancel = {},
        onBack = {},
        onFolderConfirm = {},
        onRetryAll = {},
    )
}

@Preview
@Composable
private fun PreviewPeerDroppedLight() = TetherTheme(darkTheme = false) {
    TransferProgressScreen(
        state = TransferState.PeerDropped(
            peer = fakePeer,
            fileName = "video.mp4",
            ratio = 0.45f,
        ),
        peerName = fakePeer.name,
        onCancel = {},
        onBack = {},
        onFolderConfirm = {},
        onRetryAll = {},
    )
}

@Preview
@Composable
private fun PreviewConnectionLostLight() = TetherTheme(darkTheme = false) {
    TransferProgressScreen(
        state = TransferState.ConnectionLost(
            peer = fakePeer,
            fileName = "document.pdf",
            ratio = 0.6f,
            waitingForNetwork = true,
        ),
        peerName = fakePeer.name,
        onCancel = {},
        onBack = {},
        onFolderConfirm = {},
        onRetryAll = {},
    )
}

@Preview
@Composable
private fun PreviewCancelledLight() = TetherTheme(darkTheme = false) {
    TransferProgressScreen(
        state = TransferState.Terminal.Cancelled,
        peerName = fakePeer.name,
        onCancel = {},
        onBack = {},
        onFolderConfirm = {},
        onRetryAll = {},
    )
}

@Preview
@Composable
private fun PreviewAllSuccessLight() = TetherTheme(darkTheme = false) {
    TransferSummaryScreen(
        state = TransferState.Terminal.AllSuccess(peer = fakePeer, count = 5),
        onDone = {},
        onRetryFile = {},
        onRetryAll = {},
    )
}

@Preview
@Composable
private fun PreviewAllSuccessDark() = TetherTheme(darkTheme = true) {
    TransferSummaryScreen(
        state = TransferState.Terminal.AllSuccess(peer = fakePeer, count = 1),
        onDone = {},
        onRetryFile = {},
        onRetryAll = {},
    )
}

@Preview
@Composable
private fun PreviewPartialFailureLight() = TetherTheme(darkTheme = false) {
    TransferSummaryScreen(
        state = TransferState.Terminal.PartialFailure(
            peer = fakePeer,
            sent = 3,
            total = 5,
            failed = listOf(
                FailedFile("corrupt.jpg", FailureReason.Unreadable),
                FailedFile("video.mp4", FailureReason.ReceiverWriteFailed),
            ),
        ),
        onDone = {},
        onRetryFile = {},
        onRetryAll = {},
    )
}

@Preview
@Composable
private fun PreviewAllFailedLight() = TetherTheme(darkTheme = false) {
    TransferSummaryScreen(
        state = TransferState.Terminal.AllFailed(
            peer = fakePeer,
            failed = listOf(
                FailedFile("file1.jpg", FailureReason.ConnectionLost),
                FailedFile("file2.pdf", FailureReason.ConnectionLost),
            ),
        ),
        onDone = {},
        onRetryFile = {},
        onRetryAll = {},
    )
}

@Preview
@Composable
private fun PreviewConnectionErrorSummaryLight() = TetherTheme(darkTheme = false) {
    TransferSummaryScreen(
        state = TransferState.Terminal.ConnectionErrorSummary(
            peer = fakePeer,
            sent = 2,
            total = 6,
            failed = listOf(
                FailedFile("file3.jpg", FailureReason.ConnectionLost),
                FailedFile("file4.png", FailureReason.ConnectionLost),
                FailedFile("file5.mov", FailureReason.ConnectionLost),
                FailedFile("file6.txt", FailureReason.ConnectionLost),
            ),
        ),
        onDone = {},
        onRetryFile = {},
        onRetryAll = {},
    )
}
