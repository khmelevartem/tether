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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiverEventRoutingTest {
    private val peerA = PeerIdentity("peer-a")
    private val peerB = PeerIdentity("peer-b")

    private fun buildRouter(
        inbound: MutableSharedFlow<InboundEvent>,
        scope: kotlinx.coroutines.CoroutineScope,
    ): InboundEventRouter = InboundEventRouter(scope = scope, inboundEvents = inbound)

    @Test
    fun `BatchStarted emits Started with declared totalFiles`() = runTest {
        val inbound = MutableSharedFlow<InboundEvent>(extraBufferCapacity = 16)
        val router = buildRouter(inbound, backgroundScope)
        val received = mutableListOf<ReceiveEvent>()
        backgroundScope.launch { router.eventsFor(peerA).toList(received) }
        runCurrent()

        inbound.emit(InboundEvent.BatchStarted(peerA, batchId = "b1", totalFiles = 3, totalBytes = null))
        runCurrent()

        assertEquals(1, received.size)
        val event = assertIs<ReceiveEvent.Started>(received[0])
        assertEquals(3, event.totalFiles)
    }

    @Test
    fun `FileStarted with no prior BatchStarted synthesizes implicit single-file batch and emits Started`() = runTest {
        val inbound = MutableSharedFlow<InboundEvent>(extraBufferCapacity = 16)
        val router = buildRouter(inbound, backgroundScope)
        val received = mutableListOf<ReceiveEvent>()
        backgroundScope.launch { router.eventsFor(peerA).toList(received) }
        runCurrent()

        inbound.emit(InboundEvent.FileStarted(peerA, "file.txt"))
        runCurrent()

        assertEquals(1, received.size)
        val event = assertIs<ReceiveEvent.Started>(received[0])
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
        assertIs<ReceiveEvent.Started>(eventsA[0])
        assertIs<ReceiveEvent.Started>(eventsB[0])
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

        inbound.emit(InboundEvent.FileStarted(peerA, "file.txt"))
        inbound.emit(InboundEvent.FileCompleted(peerA, "file.txt", "/downloads/file.txt"))
        runCurrent()

        assertTrue(received.any { it is ReceiveEvent.FileCompleted && it.name == "file.txt" })
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
    fun `FileCompleted count reaching totalFiles from BatchStarted synthesizes BatchCompleted`() = runTest {
        val inbound = MutableSharedFlow<InboundEvent>(extraBufferCapacity = 16)
        val router = buildRouter(inbound, backgroundScope)
        val received = mutableListOf<ReceiveEvent>()
        backgroundScope.launch { router.eventsFor(peerA).toList(received) }
        runCurrent()

        inbound.emit(InboundEvent.BatchStarted(peerA, batchId = "b1", totalFiles = 2, totalBytes = null))
        inbound.emit(InboundEvent.FileCompleted(peerA, "a.txt", null))
        inbound.emit(InboundEvent.FileCompleted(peerA, "b.txt", null))
        runCurrent()

        val batches = received.filterIsInstance<ReceiveEvent.BatchCompleted>()
        assertEquals(1, batches.size)
        assertEquals(2, batches[0].received)
        assertEquals(2, batches[0].total)
    }

    @Test
    fun `implicit single-file batch completes via FileCompleted count`() = runTest {
        val inbound = MutableSharedFlow<InboundEvent>(extraBufferCapacity = 16)
        val router = buildRouter(inbound, backgroundScope)
        val received = mutableListOf<ReceiveEvent>()
        backgroundScope.launch { router.eventsFor(peerA).toList(received) }
        runCurrent()

        inbound.emit(InboundEvent.FileStarted(peerA, "file.txt"))
        inbound.emit(InboundEvent.FileCompleted(peerA, "file.txt", null))
        runCurrent()

        val batches = received.filterIsInstance<ReceiveEvent.BatchCompleted>()
        assertEquals(1, batches.size)
        assertEquals(1, batches[0].received)
        assertEquals(1, batches[0].total)
    }

    @Test
    fun `new batchId resets per-peer counter`() = runTest {
        val inbound = MutableSharedFlow<InboundEvent>(extraBufferCapacity = 16)
        val router = buildRouter(inbound, backgroundScope)
        val received = mutableListOf<ReceiveEvent>()
        backgroundScope.launch { router.eventsFor(peerA).toList(received) }
        runCurrent()

        inbound.emit(InboundEvent.BatchStarted(peerA, batchId = "b1", totalFiles = 2, totalBytes = null))
        inbound.emit(InboundEvent.FileCompleted(peerA, "a.txt", null))
        // new batch id before first one completed — resets counter
        inbound.emit(InboundEvent.BatchStarted(peerA, batchId = "b2", totalFiles = 1, totalBytes = null))
        inbound.emit(InboundEvent.FileCompleted(peerA, "b.txt", null))
        runCurrent()

        // Only the second batch should complete (b2 with 1 file)
        val batches = received.filterIsInstance<ReceiveEvent.BatchCompleted>()
        assertEquals(1, batches.size)
        assertEquals(1, batches[0].received)
        assertEquals(1, batches[0].total)
    }

    @Test
    fun `receiver-cancel on in-flight batch synthesizes partial BatchCompleted then ConnectionLost`() = runTest {
        val inbound = MutableSharedFlow<InboundEvent>(extraBufferCapacity = 16)
        val router = buildRouter(inbound, backgroundScope)
        val received = mutableListOf<ReceiveEvent>()
        backgroundScope.launch { router.eventsFor(peerA).toList(received) }
        runCurrent()

        inbound.emit(InboundEvent.BatchStarted(peerA, batchId = "b1", totalFiles = 3, totalBytes = null))
        inbound.emit(InboundEvent.FileCompleted(peerA, "a.txt", null))
        inbound.emit(InboundEvent.ConnectionLost(peerA, receivedSoFar = 1, cancelled = true))
        runCurrent()

        val batchCompleted = received.filterIsInstance<ReceiveEvent.BatchCompleted>()
        assertEquals(1, batchCompleted.size)
        assertEquals(1, batchCompleted[0].received)
        assertEquals(3, batchCompleted[0].total)

        val connLost = received.filterIsInstance<ReceiveEvent.ConnectionLost>()
        assertEquals(1, connLost.size)
        assertEquals(1, connLost[0].receivedSoFar)
    }

    @Test
    fun `genuine ConnectionLost emits ConnectionLost with real receivedSoFar`() = runTest {
        val inbound = MutableSharedFlow<InboundEvent>(extraBufferCapacity = 16)
        val router = buildRouter(inbound, backgroundScope)
        val received = mutableListOf<ReceiveEvent>()
        backgroundScope.launch { router.eventsFor(peerA).toList(received) }
        runCurrent()

        inbound.emit(InboundEvent.BatchStarted(peerA, batchId = "b1", totalFiles = 3, totalBytes = null))
        inbound.emit(InboundEvent.FileCompleted(peerA, "a.txt", null))
        inbound.emit(InboundEvent.ConnectionLost(peerA, receivedSoFar = 1, cancelled = false))
        runCurrent()

        val batchCompleted = received.filterIsInstance<ReceiveEvent.BatchCompleted>()
        assertEquals(0, batchCompleted.size, "genuine drop must not emit BatchCompleted")

        val connLost = received.filterIsInstance<ReceiveEvent.ConnectionLost>()
        assertEquals(1, connLost.size)
        assertEquals(1, connLost[0].receivedSoFar, "receivedSoFar must reflect actual received count")
    }

    @Test
    fun `peer state resets after ConnectionLost so next batch starts fresh`() = runTest {
        val inbound = MutableSharedFlow<InboundEvent>(extraBufferCapacity = 16)
        val router = buildRouter(inbound, backgroundScope)
        val received = mutableListOf<ReceiveEvent>()
        backgroundScope.launch { router.eventsFor(peerA).toList(received) }
        runCurrent()

        inbound.emit(InboundEvent.BatchStarted(peerA, batchId = "b1", totalFiles = 1, totalBytes = null))
        inbound.emit(InboundEvent.FileCompleted(peerA, "a.txt", null))
        inbound.emit(InboundEvent.ConnectionLost(peerA, receivedSoFar = 1))
        runCurrent()

        inbound.emit(InboundEvent.BatchStarted(peerA, batchId = "b2", totalFiles = 2, totalBytes = null))
        runCurrent()

        val started = received.filterIsInstance<ReceiveEvent.Started>()
        val lastStarted = started.last()
        assertEquals(2, lastStarted.totalFiles)
    }

    @Test
    fun `aggregate receiveEvents flow carries all events tagged with peer`() = runTest {
        val inbound = MutableSharedFlow<InboundEvent>(extraBufferCapacity = 16)
        val router = buildRouter(inbound, backgroundScope)
        val aggregate = mutableListOf<Pair<PeerIdentity, ReceiveEvent>>()
        backgroundScope.launch { router.receiveEvents.toList(aggregate) }
        runCurrent()

        inbound.emit(InboundEvent.BatchStarted(peerA, batchId = "b1", totalFiles = 1, totalBytes = null))
        inbound.emit(InboundEvent.FileCompleted(peerA, "a.txt", null))
        runCurrent()

        assertTrue(aggregate.isNotEmpty())
        assertTrue(aggregate.all { it.first == peerA })
        assertTrue(aggregate.any { it.second is ReceiveEvent.Started })
        assertTrue(aggregate.any { it.second is ReceiveEvent.FileCompleted })
        assertTrue(aggregate.any { it.second is ReceiveEvent.BatchCompleted })
    }
}
