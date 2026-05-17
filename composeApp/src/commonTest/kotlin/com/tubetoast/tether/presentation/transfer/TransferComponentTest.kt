package com.tubetoast.tether.presentation.transfer

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.SendResult
import com.tubetoast.tether.transfer.FakeFileSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TransferComponentTest {
    private val peer = Device("Peer", "127.0.0.1", 8080)
    private var exitCalled = false

    private fun buildComponent(
        sources: List<FakeFileSource>,
        sender: BatchSender,
        scope: CoroutineScope,
    ): TransferComponent {
        val lifecycle = LifecycleRegistry()
        val ctx = DefaultComponentContext(lifecycle)
        lifecycle.resume()
        return TransferComponent(
            componentContext = ctx,
            peer = peer,
            sources = sources,
            batchSender = sender,
            onExit = { exitCalled = true },
            scope = scope,
        )
    }

    private fun successBatchSender() = BatchSender(
        sendOne = { _, _, _, _, onProgress ->
            onProgress(100L, 100L)
            SendResult.Success("path")
        },
        clock = { 1000L },
    )

    @Test
    fun preparingToInProgressToAllSuccess() = runTest {
        val source = FakeFileSource("file.txt", ByteArray(100), size = 100L)
        val component = buildComponent(listOf(source), successBatchSender(), backgroundScope)

        assertIs<TransferState.Preparing>(component.state.value)

        runCurrent()

        assertIs<TransferState.Terminal.AllSuccess>(component.state.value)
    }

    @Test
    fun folderConfirmGateHoldsUntilConfirmed() = runTest {
        val sources = List(FILE_COUNT_THRESHOLD) {
            FakeFileSource("f$it.txt", ByteArray(1), size = 1L)
        }
        val component = buildComponent(sources, successBatchSender(), backgroundScope)

        val state = component.state.value
        assertIs<TransferState.FolderConfirm>(state)

        component.onFolderConfirm(true)
        runCurrent()

        assertIs<TransferState.Terminal.AllSuccess>(component.state.value)
    }

    @Test
    fun cancelConfirmThenConfirmedLeadsToCancelled() = runTest {
        var sendStarted = false
        val blockingSender = BatchSender(
            sendOne = { _, _, _, _, _ ->
                sendStarted = true
                // Block indefinitely — cancel will interrupt
                kotlinx.coroutines.delay(Long.MAX_VALUE)
                SendResult.Success("path")
            },
            clock = { 0L },
        )
        val source = FakeFileSource("file.txt", ByteArray(100), size = 100L)
        val component = buildComponent(listOf(source), blockingSender, backgroundScope)

        runCurrent()
        // Force an InProgress state for cancel dialog test
        component.onCancelConfirmed()

        assertIs<TransferState.Terminal.Cancelled>(component.state.value)
    }

    @Test
    fun partialFailureTerminal() = runTest {
        var callCount = 0
        val mixedSender = BatchSender(
            sendOne = { _, _, _, _, onProgress ->
                callCount++
                if (callCount % 2 == 0) {
                    SendResult.Failure("write error")
                } else {
                    onProgress(100L, 100L)
                    SendResult.Success("path")
                }
            },
            clock = { 0L },
        )
        val sources = List(4) { FakeFileSource("f$it.txt", ByteArray(100), size = 100L) }
        val component = buildComponent(sources, mixedSender, backgroundScope)

        runCurrent()

        val terminal = component.state.value
        assertIs<TransferState.Terminal.PartialFailure>(terminal)
        assertTrue(terminal.sent > 0)
        assertTrue(terminal.failed.isNotEmpty())
    }
}
