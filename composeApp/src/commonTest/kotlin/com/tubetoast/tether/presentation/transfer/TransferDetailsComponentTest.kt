package com.tubetoast.tether.presentation.transfer

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.tubetoast.tether.peer.Peer
import com.tubetoast.tether.preferences.FakeFileTransferPreferences
import com.tubetoast.tether.preferences.FakePeerPreferencesStore
import com.tubetoast.tether.presentation.banners.PeerConflictRelay
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.PeerIdentity
import com.tubetoast.tether.transfer.FailureReason
import com.tubetoast.tether.transfer.FakeFilePicker
import com.tubetoast.tether.transfer.FakeFileSource
import com.tubetoast.tether.transfer.FileSource
import com.tubetoast.tether.transfer.PeerTransferEngine
import com.tubetoast.tether.transfer.PeerTransferState
import com.tubetoast.tether.transfer.PendingFilesRepository
import com.tubetoast.tether.transfer.PerFileStatus
import com.tubetoast.tether.transfer.ReceiverWriteFailedException
import com.tubetoast.tether.transfer.fakeBatchSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class TransferDetailsComponentTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val peer = Peer(
        id = PeerIdentity("test-peer"),
        device = Device(name = "TestDevice", host = "127.0.0.1", port = 8080),
    )

    private fun buildPeerComponent(
        scope: kotlinx.coroutines.CoroutineScope,
        pauseChannel: Channel<Unit>? = null,
        sendOneOverride: (suspend (FileSource, (Long, Long?) -> Unit) -> Unit)? = null,
    ): PeerTransferComponent {
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val context = DefaultComponentContext(lifecycle)
        val engine = PeerTransferEngine(
            peer = peer.id,
            batchSenderFactory = fakeBatchSender(sendOneOverride = sendOneOverride, pauseChannel = pauseChannel),
            inboundEvents = MutableSharedFlow(),
            scope = scope,
            peerPreferencesStore = FakePeerPreferencesStore(),
            cancelBatch = { },
        )
        return PeerTransferComponent(
            componentContext = context,
            peer = peer,
            lifecycleRegistry = lifecycle,
            engine = engine,
            onShowDetails = {},
            scope = scope,
            pendingFilesRepository = PendingFilesRepository(),
            filePicker = FakeFilePicker(result = emptyList()),
            conflictRelay = PeerConflictRelay(),
            fileTransferPreferences = FakeFileTransferPreferences(),
            onPeerChosen = {},
        )
    }

    private fun buildDetails(
        peerComponent: PeerTransferComponent,
        onBack: () -> Unit = {},
    ): TransferDetailsComponent {
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val context = DefaultComponentContext(lifecycle)
        return TransferDetailsComponent(
            componentContext = context,
            peerComponent = peerComponent,
            onBack = onBack,
        )
    }

    @Test
    fun `onCancelTransfer cancels active transfer`() = runTest {
        val pauseChannel = Channel<Unit>(0)
        val peerComponent = buildPeerComponent(scope = backgroundScope, pauseChannel = pauseChannel)
        val details = buildDetails(peerComponent)

        peerComponent.startOutbound(listOf(FakeFileSource("a.txt", 100L), FakeFileSource("b.txt", 100L)))
        runCurrent()
        assertIs<PeerTransferState.ActiveOutbound>(peerComponent.state.value.transfer)

        details.onCancelTransfer()
        runCurrent()

        assertIs<PeerTransferState.Cancelled>(peerComponent.state.value.transfer)
    }

    @Test
    fun `onBack invokes callback`() = runTest {
        var backCalled = false
        val peerComponent = buildPeerComponent(scope = backgroundScope)
        val details = buildDetails(peerComponent, onBack = { backCalled = true })

        details.onBack()

        assertEquals(true, backCalled)
    }

    @Test
    fun `onCancelFile for InProgress file cancels only that file and batch continues`() = runTest {
        val fileAPause = Channel<Unit>(Channel.UNLIMITED)
        val fileBPause = Channel<Unit>(0)
        val fileCPause = Channel<Unit>(Channel.UNLIMITED)

        fileAPause.send(Unit)
        fileCPause.send(Unit)

        val peerComponent = buildPeerComponent(
            scope = backgroundScope,
            sendOneOverride = { src, onProgress ->
                when (src.name) {
                    "a.txt" -> fileAPause.receive()
                    "b.txt" -> fileBPause.receive()
                    "c.txt" -> fileCPause.receive()
                }
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
            },
        )
        val details = buildDetails(peerComponent)

        peerComponent.startOutbound(
            listOf(FakeFileSource("a.txt", 100L), FakeFileSource("b.txt", 100L), FakeFileSource("c.txt", 100L)),
        )
        runCurrent()

        val stateOnB = assertIs<PeerTransferState.ActiveOutbound.Sending>(peerComponent.state.value.transfer)
        assertEquals("b.txt", stateOnB.currentFile)

        details.onCancelFile("b.txt")
        runCurrent()
        runCurrent()

        val finalState = peerComponent.state.value.transfer
        assertIs<PeerTransferState.Sent>(finalState)
        assertEquals(2, finalState.sent)
        assertEquals(3, finalState.total)

        val bStatus = finalState.perFile.first { it.name == "b.txt" }
        assertIs<PerFileStatus.Failed>(bStatus)
        assertEquals(FailureReason.CancelledByUser, bStatus.reason)

        val aStatus = finalState.perFile.first { it.name == "a.txt" }
        assertIs<PerFileStatus.Done>(aStatus)

        val cStatus = finalState.perFile.first { it.name == "c.txt" }
        assertIs<PerFileStatus.Done>(cStatus)
    }

    @Test
    fun `onCancelFile for Queued file marks it Failed and batch skips it`() = runTest {
        val pauseChannel = Channel<Unit>(0)
        val peerComponent = buildPeerComponent(scope = backgroundScope, pauseChannel = pauseChannel)
        val details = buildDetails(peerComponent)

        peerComponent.startOutbound(
            listOf(FakeFileSource("file1.txt", 100L), FakeFileSource("file2.txt", 200L)),
        )
        runCurrent()

        val activeState = assertIs<PeerTransferState.ActiveOutbound.Sending>(peerComponent.state.value.transfer)
        assertEquals("file1.txt", activeState.currentFile)

        details.onCancelFile("file2.txt")
        runCurrent()

        val stateAfterCancel = peerComponent.state.value.transfer
        assertIs<PeerTransferState.ActiveOutbound>(stateAfterCancel)
        val file2Status = stateAfterCancel.perFile.first { it.name == "file2.txt" }
        assertIs<PerFileStatus.Failed>(file2Status)
        assertEquals(FailureReason.CancelledByUser, file2Status.reason)
        assertEquals(true, file2Status.cancelledByUser)

        pauseChannel.send(Unit)
        runCurrent()

        val finalState = peerComponent.state.value.transfer
        assertIs<PeerTransferState.Sent>(finalState)
        val finalFile2 = finalState.perFile.first { it.name == "file2.txt" }
        assertIs<PerFileStatus.Failed>(finalFile2)
        assertEquals(FailureReason.CancelledByUser, finalFile2.reason)
        assertEquals(true, finalFile2.cancelledByUser)
    }

    @Test
    fun `onRetryAll re-runs failed files via peerComponent`() = runTest {
        val sendCalls = mutableMapOf("a.txt" to 0, "b.txt" to 0)
        val peerComponent = buildPeerComponent(
            scope = backgroundScope,
            sendOneOverride = { src, onProgress ->
                sendCalls[src.name] = (sendCalls[src.name] ?: 0) + 1
                if (src.name == "b.txt" && sendCalls["b.txt"] == 1) {
                    throw ReceiverWriteFailedException(507)
                }
                onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
            },
        )
        val details = buildDetails(peerComponent)

        peerComponent.startOutbound(listOf(FakeFileSource("a.txt", 100L), FakeFileSource("b.txt", 100L)))
        runCurrent()
        assertIs<PeerTransferState.Sent>(peerComponent.state.value.transfer)

        details.onRetryAll()
        runCurrent()

        assertEquals(1, sendCalls["a.txt"])
        assertEquals(2, sendCalls["b.txt"])
        assertNull((peerComponent.state.value.transfer as? PeerTransferState.Sent)?.partialReason)
    }
}
