package com.tubetoast.tether.presentation.transfer

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.tubetoast.tether.transfer.FailureReason
import com.tubetoast.tether.transfer.FakeConnectionMonitor
import com.tubetoast.tether.transfer.FakeFileSource
import com.tubetoast.tether.transfer.FileSource
import com.tubetoast.tether.transfer.PeerIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class TransferDetailsComponentTest {
    private val peer = PeerIdentity("test-peer")

    private fun buildPeerComponent(
        scope: kotlinx.coroutines.CoroutineScope,
        pauseChannel: Channel<Unit>? = null,
        sendOneOverride: (suspend (FileSource, (Long, Long?) -> Unit) -> Unit)? = null,
    ): DefaultPeerTransferComponent {
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val context = DefaultComponentContext(lifecycle)
        val monitor = FakeConnectionMonitor()
        return DefaultPeerTransferComponent(
            componentContext = context,
            peer = peer,
            batchSenderFactory = {
                BatchSender(
                    sendOne = sendOneOverride ?: { src, onProgress ->
                        pauseChannel?.receive()
                        onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
                    },
                    connectionMonitor = monitor,
                    progressThrottle = 100.milliseconds,
                    dispatcher = Dispatchers.Unconfined,
                )
            },
            inboundEvents = MutableSharedFlow(),
            onShowDetailsCallback = {},
            connectionMonitor = monitor,
            scope = scope,
        )
    }

    private fun buildDetails(
        peerComponent: PeerTransferComponent,
        onBack: () -> Unit = {},
    ): DefaultTransferDetailsComponent {
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val context = DefaultComponentContext(lifecycle)
        return DefaultTransferDetailsComponent(
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
        assertIs<PeerTransferState.ActiveOutbound>(peerComponent.state.value)

        details.onCancelTransfer()
        runCurrent()

        assertIs<PeerTransferState.Cancelled>(peerComponent.state.value)
    }

    @Test
    fun `onRetryAll delegates to peer component`() = runTest {
        var retryCalled = false
        val lifecycle = LifecycleRegistry()
        lifecycle.resume()
        val context = DefaultComponentContext(lifecycle)

        val fakePeer = object : PeerTransferComponent {
            override val peer = PeerIdentity("fake-peer")
            override val state = MutableValue<PeerTransferState>(PeerTransferState.Idle(this.peer))

            override fun toggleExpanded() {}

            override fun startOutbound(sources: List<FileSource>) {}

            override fun onCancel() {}

            override fun onRetry() {
                retryCalled = true
            }

            override fun onRetryFile(name: String) {}

            override fun onCancelFile(name: String) {}

            override fun onDismiss() {}

            override fun onShowDetails() {}
        }
        val details = DefaultTransferDetailsComponent(
            componentContext = context,
            peerComponent = fakePeer,
            onBack = {},
        )

        details.onRetryAll()

        assertEquals(true, retryCalled)
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

        val stateOnB = peerComponent.state.value
        assertIs<PeerTransferState.ActiveOutbound>(stateOnB)
        assertEquals("b.txt", stateOnB.currentFile)

        details.onCancelFile("b.txt")
        runCurrent()
        runCurrent()

        val finalState = peerComponent.state.value
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

        val activeState = peerComponent.state.value
        assertIs<PeerTransferState.ActiveOutbound>(activeState)
        assertEquals("file1.txt", activeState.currentFile)

        details.onCancelFile("file2.txt")
        runCurrent()

        val stateAfterCancel = peerComponent.state.value
        assertIs<PeerTransferState.ActiveOutbound>(stateAfterCancel)
        val file2Status = stateAfterCancel.perFile.first { it.name == "file2.txt" }
        assertIs<PerFileStatus.Failed>(file2Status)
        assertEquals(FailureReason.CancelledByUser, file2Status.reason)
        assertEquals(true, file2Status.cancelledByUser)

        pauseChannel.send(Unit)
        runCurrent()

        val finalState = peerComponent.state.value
        assertIs<PeerTransferState.Sent>(finalState)
        val finalFile2 = finalState.perFile.first { it.name == "file2.txt" }
        assertIs<PerFileStatus.Failed>(finalFile2)
        assertEquals(FailureReason.CancelledByUser, finalFile2.reason)
        assertEquals(true, finalFile2.cancelledByUser)
    }
}
