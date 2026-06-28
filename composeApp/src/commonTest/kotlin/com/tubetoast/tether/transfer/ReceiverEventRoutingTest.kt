package com.tubetoast.tether.transfer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiverEventRoutingTest {
    private val peerA = PeerIdentity("peer-a")
    private val peerB = PeerIdentity("peer-b")

    private fun buildRouter(
        inbound: MutableSharedFlow<InboundEvent>,
        scope: kotlinx.coroutines.CoroutineScope,
    ): InboundEventRouter = InboundEventRouter(scope = scope, inboundEvents = inbound)

    @Test
    fun `FileStarted routes to the correct peer flow as ReceiveEvent Started`() = runTest {
        val inbound = MutableSharedFlow<InboundEvent>(extraBufferCapacity = 16)
        val router = buildRouter(inbound, backgroundScope)
        val received = mutableListOf<ReceiveEvent>()
        backgroundScope.launch { router.eventsFor(peerA).toList(received) }
        runCurrent()

        inbound.emit(InboundEvent.FileStarted(peerA, "file.txt"))
        runCurrent()

        assertEquals(1, received.size)
        val event = assertIs<ReceiveEvent.Started>(received[0])
        assertEquals("file.txt", event.currentFile)
        assertEquals(1, event.totalFiles)
    }

    @Test
    fun `events from different peers route to separate flows`() = runTest {
        val inbound = MutableSharedFlow<InboundEvent>(extraBufferCapacity = 16)
        val router = buildRouter(inbound, backgroundScope)
        val eventsA = mutableListOf<ReceiveEvent>()
        val eventsB = mutableListOf<ReceiveEvent>()
        backgroundScope.launch { router.eventsFor(peerA).toList(eventsA) }
        backgroundScope.launch { router.eventsFor(peerB).toList(eventsB) }
        runCurrent()

        inbound.emit(InboundEvent.FileStarted(peerA, "a.txt"))
        inbound.emit(InboundEvent.FileStarted(peerB, "b.txt"))
        runCurrent()

        assertEquals(1, eventsA.size)
        assertEquals(1, eventsB.size)
        assertEquals("a.txt", assertIs<ReceiveEvent.Started>(eventsA[0]).currentFile)
        assertEquals("b.txt", assertIs<ReceiveEvent.Started>(eventsB[0]).currentFile)
    }

    @Test
    fun `Progress routes peer-stripped`() = runTest {
        val inbound = MutableSharedFlow<InboundEvent>(extraBufferCapacity = 16)
        val router = buildRouter(inbound, backgroundScope)
        val received = mutableListOf<ReceiveEvent>()
        backgroundScope.launch { router.eventsFor(peerA).toList(received) }
        runCurrent()

        inbound.emit(InboundEvent.Progress(peerA, "file.txt", 500L, 1000L))
        runCurrent()

        val event = assertIs<ReceiveEvent.Progress>(received[0])
        assertEquals("file.txt", event.name)
        assertEquals(500L, event.receivedBytes)
        assertEquals(1000L, event.totalBytes)
    }

    @Test
    fun `FileCompleted routes peer-stripped`() = runTest {
        val inbound = MutableSharedFlow<InboundEvent>(extraBufferCapacity = 16)
        val router = buildRouter(inbound, backgroundScope)
        val received = mutableListOf<ReceiveEvent>()
        backgroundScope.launch { router.eventsFor(peerA).toList(received) }
        runCurrent()

        inbound.emit(InboundEvent.FileCompleted(peerA, "file.txt", "/downloads/file.txt"))
        runCurrent()

        assertIs<ReceiveEvent.FileCompleted>(received[0])
        assertEquals("file.txt", (received[0] as ReceiveEvent.FileCompleted).name)
    }

    @Test
    fun `Failed routes peer-stripped`() = runTest {
        val inbound = MutableSharedFlow<InboundEvent>(extraBufferCapacity = 16)
        val router = buildRouter(inbound, backgroundScope)
        val received = mutableListOf<ReceiveEvent>()
        backgroundScope.launch { router.eventsFor(peerA).toList(received) }
        runCurrent()

        inbound.emit(InboundEvent.Failed(peerA, "file.txt", FailureReason.NetworkLost))
        runCurrent()

        val event = assertIs<ReceiveEvent.Failed>(received[0])
        assertEquals("file.txt", event.file)
        assertEquals(FailureReason.NetworkLost, event.reason)
    }

    @Test
    fun `cancelled ConnectionLost synthesizes BatchCompleted then ConnectionLost`() = runTest {
        val inbound = MutableSharedFlow<InboundEvent>(extraBufferCapacity = 16)
        val router = buildRouter(inbound, backgroundScope)
        val received = mutableListOf<ReceiveEvent>()
        backgroundScope.launch { router.eventsFor(peerA).toList(received) }
        runCurrent()

        inbound.emit(InboundEvent.FileStarted(peerA, "a.txt"))
        inbound.emit(InboundEvent.FileCompleted(peerA, "a.txt", null))
        inbound.emit(InboundEvent.ConnectionLost(peerA, receivedSoFar = 1, cancelled = true))
        runCurrent()

        val batchCompleted = received.filterIsInstance<ReceiveEvent.BatchCompleted>()
        assertEquals(1, batchCompleted.size)
        assertEquals(1, batchCompleted[0].received)
        assertEquals(1, batchCompleted[0].total)

        val connLost = received.filterIsInstance<ReceiveEvent.ConnectionLost>()
        assertEquals(1, connLost.size)
    }

    @Test
    fun `genuine ConnectionLost emits only ConnectionLost without BatchCompleted`() = runTest {
        val inbound = MutableSharedFlow<InboundEvent>(extraBufferCapacity = 16)
        val router = buildRouter(inbound, backgroundScope)
        val received = mutableListOf<ReceiveEvent>()
        backgroundScope.launch { router.eventsFor(peerA).toList(received) }
        runCurrent()

        inbound.emit(InboundEvent.FileStarted(peerA, "a.txt"))
        inbound.emit(InboundEvent.ConnectionLost(peerA, receivedSoFar = 0, cancelled = false))
        runCurrent()

        val batchCompleted = received.filterIsInstance<ReceiveEvent.BatchCompleted>()
        assertEquals(0, batchCompleted.size, "genuine drop must not emit BatchCompleted")

        val connLost = received.filterIsInstance<ReceiveEvent.ConnectionLost>()
        assertEquals(1, connLost.size)
    }

    @Test
    fun `totalFiles grows as new files are seen within a batch`() = runTest {
        val inbound = MutableSharedFlow<InboundEvent>(extraBufferCapacity = 16)
        val router = buildRouter(inbound, backgroundScope)
        val received = mutableListOf<ReceiveEvent>()
        backgroundScope.launch { router.eventsFor(peerA).toList(received) }
        runCurrent()

        inbound.emit(InboundEvent.FileStarted(peerA, "a.txt"))
        inbound.emit(InboundEvent.FileStarted(peerA, "b.txt"))
        runCurrent()

        val started = received.filterIsInstance<ReceiveEvent.Started>()
        assertEquals(2, started.size)
        assertEquals(1, started[0].totalFiles)
        assertEquals(2, started[1].totalFiles)
    }

    @Test
    fun `peer state resets after ConnectionLost so next batch starts fresh`() = runTest {
        val inbound = MutableSharedFlow<InboundEvent>(extraBufferCapacity = 16)
        val router = buildRouter(inbound, backgroundScope)
        val received = mutableListOf<ReceiveEvent>()
        backgroundScope.launch { router.eventsFor(peerA).toList(received) }
        runCurrent()

        inbound.emit(InboundEvent.FileStarted(peerA, "a.txt"))
        inbound.emit(InboundEvent.FileCompleted(peerA, "a.txt", null))
        inbound.emit(InboundEvent.ConnectionLost(peerA, receivedSoFar = 1))
        runCurrent()

        inbound.emit(InboundEvent.FileStarted(peerA, "b.txt"))
        runCurrent()

        val started = received.filterIsInstance<ReceiveEvent.Started>()
        val lastStarted = started.last()
        assertEquals("b.txt", lastStarted.currentFile)
        assertEquals(1, lastStarted.totalFiles)
    }
}
