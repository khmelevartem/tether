package com.tubetoast.tether.presentation.transfer.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.tubetoast.tether.discovery.DeviceDiscovery
import com.tubetoast.tether.presentation.DeviceListComponent
import com.tubetoast.tether.presentation.DeviceListScreen
import com.tubetoast.tether.presentation.transfer.CancelConfirmDialog
import com.tubetoast.tether.presentation.transfer.FailedFile
import com.tubetoast.tether.presentation.transfer.FailureReason
import com.tubetoast.tether.presentation.transfer.FolderSendConfirmDialog
import com.tubetoast.tether.presentation.transfer.TransferProgressScreen
import com.tubetoast.tether.presentation.transfer.TransferState
import com.tubetoast.tether.presentation.transfer.TransferSummaryScreen
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.FileSource
import com.tubetoast.tether.ui.theme.TetherTheme
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val fakePeer = Device("MacBook Pro", "192.168.1.100", 8080)

@Preview
@Composable
private fun PreviewPreparingLight() = TetherTheme(darkTheme = false) {
    TransferProgressScreen(
        state = TransferState.Preparing,
        peerName = fakePeer.name,
        onCancel = {},
        onBack = {},
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
        onRetryAll = {},
    )
}

@Preview
@Composable
private fun PreviewInProgressWithNoticeDark() = TetherTheme(darkTheme = true) {
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
        onRetryAll = {},
    )
}

@Preview
@Composable
private fun PreviewCancelledDark() = TetherTheme(darkTheme = true) {
    TransferProgressScreen(
        state = TransferState.Terminal.Cancelled,
        peerName = fakePeer.name,
        onCancel = {},
        onBack = {},
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
private fun PreviewPartialFailureDark() = TetherTheme(darkTheme = true) {
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
private fun PreviewAllFailedDark() = TetherTheme(darkTheme = true) {
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

@Preview
@Composable
private fun PreviewConnectionErrorSummaryDark() = TetherTheme(darkTheme = true) {
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

@Preview
@Composable
private fun PreviewFolderConfirmLight() = TetherTheme(darkTheme = false) {
    FolderSendConfirmDialog(
        fileCount = 1234,
        totalBytes = 2_500_000_000L,
        onContinue = {},
        onCancel = {},
    )
}

@Preview
@Composable
private fun PreviewFolderConfirmDark() = TetherTheme(darkTheme = true) {
    FolderSendConfirmDialog(
        fileCount = 1234,
        totalBytes = 2_500_000_000L,
        onContinue = {},
        onCancel = {},
    )
}

@Preview
@Composable
private fun PreviewCancelConfirmLight() = TetherTheme(darkTheme = false) {
    TransferProgressScreen(
        state = TransferState.InProgress(
            currentFile = "photo.jpg",
            bytesDone = 10_000_000L,
            bytesTotal = 40_000_000L,
            speedBytesPerSec = 2_000_000L,
        ),
        peerName = fakePeer.name,
        onCancel = {},
        onBack = {},
        onRetryAll = {},
    )
    CancelConfirmDialog(onStopTransfer = {}, onKeepSending = {})
}

@Preview
@Composable
private fun PreviewCancelConfirmDark() = TetherTheme(darkTheme = true) {
    TransferProgressScreen(
        state = TransferState.InProgress(
            currentFile = "photo.jpg",
            bytesDone = 10_000_000L,
            bytesTotal = 40_000_000L,
            speedBytesPerSec = 2_000_000L,
        ),
        peerName = fakePeer.name,
        onCancel = {},
        onBack = {},
        onRetryAll = {},
    )
    CancelConfirmDialog(onStopTransfer = {}, onKeepSending = {})
}

@Preview
@Composable
private fun PreviewSuccessAnimationPeakLight() = TetherTheme(darkTheme = false) {
    TransferSummaryScreen(
        state = TransferState.Terminal.AllSuccess(peer = fakePeer, count = 3),
        onDone = {},
        onRetryFile = {},
        onRetryAll = {},
    )
}

@Preview
@Composable
private fun PreviewReceiverWriteFailedLight() = TetherTheme(darkTheme = false) {
    TransferProgressScreen(
        state = TransferState.InProgress(
            currentFile = "photo.jpg",
            bytesDone = 8_000_000L,
            bytesTotal = 20_000_000L,
            speedBytesPerSec = 1_200_000L,
            inlineNotice = "Couldn't save video.mp4 on ${fakePeer.name}.",
        ),
        peerName = fakePeer.name,
        onCancel = {},
        onBack = {},
        onRetryAll = {},
    )
}

@Preview
@Composable
private fun PreviewReceiverWriteFailedDark() = TetherTheme(darkTheme = true) {
    TransferProgressScreen(
        state = TransferState.InProgress(
            currentFile = "photo.jpg",
            bytesDone = 8_000_000L,
            bytesTotal = 20_000_000L,
            speedBytesPerSec = 1_200_000L,
            inlineNotice = "Couldn't save video.mp4 on ${fakePeer.name}.",
        ),
        peerName = fakePeer.name,
        onCancel = {},
        onBack = {},
        onRetryAll = {},
    )
}

private class PreviewDeviceDiscovery : DeviceDiscovery {
    override val discoveredDevices: StateFlow<List<Device>> = MutableStateFlow(
        listOf(Device("MacBook Pro", "192.168.1.100", 8080)),
    )

    override fun start(deviceName: String, port: Int) {}

    override fun stop() {}
}

private class PreviewFileSource(
    index: Int,
) : FileSource {
    override val name = "file$index.jpg"
    override val size: Long = 1024L
    override val relativePath: String? = null

    override suspend fun openReadChannel() = ByteReadChannel(ByteArray(0))
}

private fun previewDeviceListComponent(pendingCount: Int): DeviceListComponent {
    val lifecycle = LifecycleRegistry().also { it.resume() }
    val ctx = DefaultComponentContext(lifecycle)
    val fakeSources = List(pendingCount) { i -> PreviewFileSource(i) }
    return DeviceListComponent(
        componentContext = ctx,
        discovery = PreviewDeviceDiscovery(),
        pendingFiles = MutableValue(fakeSources),
    )
}

@Preview
@Composable
private fun PreviewDeviceListPendingSingleLight() = TetherTheme(darkTheme = false) {
    DeviceListScreen(component = previewDeviceListComponent(1))
}

@Preview
@Composable
private fun PreviewDeviceListPendingSingleDark() = TetherTheme(darkTheme = true) {
    DeviceListScreen(component = previewDeviceListComponent(1))
}

@Preview
@Composable
private fun PreviewDeviceListPendingMultiLight() = TetherTheme(darkTheme = false) {
    DeviceListScreen(component = previewDeviceListComponent(5))
}

@Preview
@Composable
private fun PreviewDeviceListPendingMultiDark() = TetherTheme(darkTheme = true) {
    DeviceListScreen(component = previewDeviceListComponent(5))
}
