package com.tubetoast.tether.presentation.transfer

import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.SendResult
import com.tubetoast.tether.transfer.FakeFileSource
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BatchSenderTest {
    private val peer = Device("TestPeer", "127.0.0.1", 8080)
    private var time = 0L
    private val clock = { time }

    private fun successSender(): suspend (Device, ByteReadChannel, String, Long?, (Long, Long?) -> Unit) -> SendResult =
        { _, _, _, _, onProgress ->
            onProgress(100L, 100L)
            SendResult.Success("path")
        }

    private fun failureSender(
        reason: String = "write error",
    ): suspend (Device, ByteReadChannel, String, Long?, (Long, Long?) -> Unit) -> SendResult =
        { _, _, _, _, _ ->
            SendResult.Failure(reason)
        }

    @Test
    fun happyPathSingleFile() = runTest {
        val sender = BatchSender(sendOne = successSender(), clock = clock)
        val source = FakeFileSource("file.txt", ByteArray(100), size = 100L)
        val states = mutableListOf<TransferState>()

        val outcome = sender.run(peer, listOf(source)) { states += it }

        assertEquals(1, outcome.sent)
        assertEquals(1, outcome.total)
        assertTrue(outcome.failed.isEmpty())
        assertFalse(outcome.connectionLostMidway)
    }

    @Test
    fun multiFileAllSuccess() = runTest {
        val sender = BatchSender(sendOne = successSender(), clock = clock)
        val sources = List(3) { FakeFileSource("file$it.txt", ByteArray(50), size = 50L) }

        val outcome = sender.run(peer, sources) {}

        assertEquals(3, outcome.sent)
        assertEquals(3, outcome.total)
        assertTrue(outcome.failed.isEmpty())
    }

    @Test
    fun perFileFailureContinuesBatch() = runTest {
        var callCount = 0
        val mixedSender: suspend (Device, ByteReadChannel, String, Long?, (Long, Long?) -> Unit) -> SendResult =
            { _, _, _, _, onProgress ->
                callCount++
                if (callCount == 2) {
                    SendResult.Failure("write error")
                } else {
                    onProgress(100L, 100L)
                    SendResult.Success("path")
                }
            }
        val sender = BatchSender(sendOne = mixedSender, clock = clock)
        val sources = List(3) { FakeFileSource("file$it.txt", ByteArray(100), size = 100L) }

        val outcome = sender.run(peer, sources) {}

        assertEquals(2, outcome.sent)
        assertEquals(1, outcome.failed.size)
        assertFalse(outcome.connectionLostMidway)
    }

    @Test
    fun cancellationBetweenFilesThrowsCancellationException() = runTest {
        var callCount = 0
        val slowSender: suspend (Device, ByteReadChannel, String, Long?, (Long, Long?) -> Unit) -> SendResult =
            { _, _, _, _, _ ->
                callCount++
                delay(1_000)
                SendResult.Success("path")
            }
        val sender = BatchSender(sendOne = slowSender, clock = clock)
        val sources = List(5) { FakeFileSource("file$it.txt") }

        var outcome: BatchOutcome? = null
        val job = launch {
            outcome = sender.run(peer, sources) {}
        }
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertTrue(callCount < 5, "Only $callCount file(s) should have been attempted before cancellation")
        assertNull(outcome, "Outcome must be null when cancelled before completion")
    }

    @Test
    fun connectionLostMidwayStopsAndMarksRemainingFiles() = runTest {
        var callCount = 0
        val connectErrSender: suspend (Device, ByteReadChannel, String, Long?, (Long, Long?) -> Unit) -> SendResult =
            { _, _, _, _, onProgress ->
                callCount++
                if (callCount == 2) {
                    SendResult.Failure("connect error reset by peer")
                } else {
                    onProgress(100L, 100L)
                    SendResult.Success("path")
                }
            }
        val sender = BatchSender(sendOne = connectErrSender, clock = clock)
        val sources = List(4) { FakeFileSource("f$it.txt", ByteArray(100), size = 100L) }

        val outcome = sender.run(peer, sources) {}

        assertEquals(1, outcome.sent)
        assertTrue(outcome.connectionLostMidway)
        assertEquals(3, outcome.failed.size, "Files 2, 3, 4 should all be in failed list")
        assertTrue(outcome.failed.all { it.reason == FailureReason.ConnectionLost })
        assertEquals(2, callCount, "Should stop attempting after connection-error on file 2")
    }

    @Test
    fun unreadableFileIsSkippedAndMarkedInOutcome() = runTest {
        val throwingSource = object : com.tubetoast.tether.transfer.FileSource {
            override val name = "bad.bin"
            override val size: Long? = 100L
            override val relativePath: String? = null

            override suspend fun openReadChannel(): ByteReadChannel = throw RuntimeException("permission denied")
        }
        val goodSource = FakeFileSource("good.txt", ByteArray(50), size = 50L)
        val sender = BatchSender(sendOne = successSender(), clock = clock)

        val outcome = sender.run(peer, listOf(throwingSource, goodSource)) {}

        assertEquals(1, outcome.sent)
        assertEquals(1, outcome.failed.size)
        assertEquals("bad.bin", outcome.failed[0].name)
        assertEquals(FailureReason.Unreadable, outcome.failed[0].reason)
    }

    @Test
    fun aggregateByteCounterIsMonotone() = runTest {
        val sender = BatchSender(sendOne = successSender(), clock = clock)
        val sources = List(3) { FakeFileSource("file$it.txt", ByteArray(100), size = 100L) }
        val bytesDoneValues = mutableListOf<Long>()

        sender.run(peer, sources) { state ->
            if (state is TransferState.InProgress) {
                bytesDoneValues += state.bytesDone
            }
        }

        for (i in 1 until bytesDoneValues.size) {
            assertTrue(
                bytesDoneValues[i] >= bytesDoneValues[i - 1],
                "Bytes done should be monotone: ${bytesDoneValues[i - 1]} > ${bytesDoneValues[i]}",
            )
        }
    }
}
