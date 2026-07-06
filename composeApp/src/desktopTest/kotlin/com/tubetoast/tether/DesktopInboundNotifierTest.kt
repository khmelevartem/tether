package com.tubetoast.tether

import com.tubetoast.tether.protocol.PeerIdentity
import com.tubetoast.tether.transfer.ReceiveEvent
import com.tubetoast.tether.transfer.WindowHolder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * `bringWindowToFront` and the tray notification both bottom out in AWT APIs
 * ([java.awt.SystemTray] unsupported in CI) that throw [java.awt.HeadlessException] outside a
 * real display, so this test cannot observe their effects directly. It instead drives every
 * [ReceiveEvent] variant through the collector and relies on `runTest` failing on an uncaught
 * exception from the collector coroutine — the `assertNull` below only confirms the guard clause
 * in `bringWindowToFront` was taken (no window registered) rather than a crash.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopInboundNotifierTest {
    private val peer = PeerIdentity("peer-1")
    private val windowHolder = WindowHolder()

    @Test
    fun `all receive event types are consumed without error in headless mode`() = runTest {
        val flow = MutableSharedFlow<Pair<PeerIdentity, ReceiveEvent>>(extraBufferCapacity = 8)
        DesktopInboundNotifier(windowHolder, flow, backgroundScope).start()
        runCurrent()

        flow.emit(peer to ReceiveEvent.Started(currentFile = "a.txt", totalFiles = 2))
        flow.emit(peer to ReceiveEvent.Progress(name = "a.txt", receivedBytes = 500, totalBytes = 1000))
        flow.emit(peer to ReceiveEvent.BatchCompleted(received = 2, total = 2))
        flow.emit(peer to ReceiveEvent.ConnectionLost(receivedSoFar = 2))
        runCurrent()

        assertNull(windowHolder.window, "no window registered — bringWindowToFront must no-op, not throw")
    }
}
