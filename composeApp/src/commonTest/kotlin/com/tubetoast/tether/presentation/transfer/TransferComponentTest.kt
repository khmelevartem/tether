package com.tubetoast.tether.presentation.transfer

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.SendResult
import com.tubetoast.tether.transfer.FakeFileSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
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
            blockingDispatcher = UnconfinedTestDispatcher(),
        )
    }

    private fun successBatchSender() = BatchSender(
        sendOne = { _, _, _, _, onProgress ->
            onProgress(100L, 100L)
            SendResult.Success("path")
        },
        clock = { 1000L },
    )

    private fun allFailSender() = BatchSender(
        sendOne = { _, _, _, _, _ ->
            SendResult.Failure("write error")
        },
        clock = { 0L },
    )

    private fun connectionLostSender() = BatchSender(
        sendOne = { _, _, _, _, _ ->
            SendResult.Failure("connect error reset by peer")
        },
        clock = { 0L },
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

        runCurrent() // advance init's scope.launch to resolve threshold check
        val state = component.state.value
        assertIs<TransferState.FolderConfirm>(state)

        component.onFolderConfirm(true)
        runCurrent()

        assertIs<TransferState.Terminal.AllSuccess>(component.state.value)
    }

    @Test
    fun folderConfirmDeniedExitsWithoutSending() = runTest {
        val sources = List(FILE_COUNT_THRESHOLD) {
            FakeFileSource("f$it.txt", ByteArray(1), size = 1L)
        }
        var startBatchCalled = false
        val trackingSender = BatchSender(
            sendOne = { _, _, _, _, _ ->
                startBatchCalled = true
                SendResult.Success("path")
            },
            clock = { 0L },
        )
        val component = buildComponent(sources, trackingSender, backgroundScope)

        runCurrent() // advance init's scope.launch to resolve threshold check
        assertIs<TransferState.FolderConfirm>(component.state.value)
        component.onFolderConfirm(false)

        assertTrue(exitCalled, "onExit must be called when folder confirm is denied")
        assertFalse(startBatchCalled, "startBatch must not be called when folder confirm is denied")
    }

    @Test
    fun cancelConfirmThenConfirmedLeadsToCancelled() = runTest {
        val blockingSender = BatchSender(
            sendOne = { _, _, _, _, _ ->
                kotlinx.coroutines.delay(Long.MAX_VALUE)
                SendResult.Success("path")
            },
            clock = { 0L },
        )
        val source = FakeFileSource("file.txt", ByteArray(100), size = 100L)
        val component = buildComponent(listOf(source), blockingSender, backgroundScope)

        runCurrent()
        component.onCancelClicked()
        assertIs<TransferState.CancelConfirm>(component.state.value)

        component.onCancelConfirmed()
        assertIs<TransferState.Terminal.Cancelled>(component.state.value)
    }

    @Test
    fun cancelConfirmSurvivesKeepSending() = runTest {
        // Verifies CancelConfirm is stable: onKeepSending restores the snapshot,
        // which is only possible if CancelConfirm was not overwritten between
        // onCancelClicked and onKeepSending.
        val blockingSender = BatchSender(
            sendOne = { _, _, _, _, _ ->
                kotlinx.coroutines.delay(Long.MAX_VALUE)
                SendResult.Success("path")
            },
            clock = { 0L },
        )
        val source = FakeFileSource("file.txt", ByteArray(100), size = 100L)
        val component = buildComponent(listOf(source), blockingSender, backgroundScope)

        runCurrent()
        component.onCancelClicked()
        assertIs<TransferState.CancelConfirm>(component.state.value)

        component.onKeepSending()
        // Snapshot was the InProgress placeholder set by onCancelClicked when in Preparing state
        assertIs<TransferState.InProgress>(component.state.value)
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

    @Test
    fun allFailedTerminalWhenNothingSent() = runTest {
        val sources = List(3) { FakeFileSource("f$it.txt", ByteArray(100), size = 100L) }
        val component = buildComponent(sources, allFailSender(), backgroundScope)

        runCurrent()

        assertIs<TransferState.Terminal.AllFailed>(component.state.value)
    }

    @Test
    fun connectionErrorSummaryTerminalOnConnectionLoss() = runTest {
        val sources = List(4) { FakeFileSource("f$it.txt", ByteArray(100), size = 100L) }
        val component = buildComponent(sources, connectionLostSender(), backgroundScope)

        runCurrent()

        assertIs<TransferState.Terminal.ConnectionErrorSummary>(component.state.value)
    }

    @Test
    fun onRetryFileRebuildsBatchWithSingleSource() = runTest {
        val failNames = mutableSetOf("bad.txt")
        val mixedSender = BatchSender(
            sendOne = { _, _, name, _, onProgress ->
                if (name in failNames) {
                    failNames -= name
                    SendResult.Failure("write error")
                } else {
                    onProgress(100L, 100L)
                    SendResult.Success("path")
                }
            },
            clock = { 0L },
        )
        val sources = listOf(
            FakeFileSource("ok.txt", ByteArray(100), size = 100L),
            FakeFileSource("bad.txt", ByteArray(100), size = 100L),
        )
        val component = buildComponent(sources, mixedSender, backgroundScope)
        runCurrent()

        val terminal = component.state.value
        assertIs<TransferState.Terminal.PartialFailure>(terminal)
        assertTrue(terminal.failed.any { it.name == "bad.txt" })

        component.onRetryFile("bad.txt")
        runCurrent()

        assertIs<TransferState.Terminal.AllSuccess>(component.state.value)
    }

    @Test
    fun onRetryAllRebuildsBatchWithFailedFiles() = runTest {
        val sources = List(3) { FakeFileSource("f$it.txt", ByteArray(100), size = 100L) }
        val component = buildComponent(sources, connectionLostSender(), backgroundScope)
        runCurrent()

        val terminal = component.state.value
        assertIs<TransferState.Terminal.ConnectionErrorSummary>(terminal)
        assertTrue(terminal.failed.isNotEmpty())

        component.onRetryAll()
        runCurrent()

        assertIs<TransferState.Terminal.ConnectionErrorSummary>(component.state.value)
    }
}
