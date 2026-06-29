package com.tubetoast.tether

import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.transfer.ReceiveEvent
import com.tubetoast.tether.transfer.WindowHolder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

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
        // ConnectionLost must be silently ignored — no tray or focus call (no handler for it).
        flow.emit(peer to ReceiveEvent.ConnectionLost(receivedSoFar = 2))
        runCurrent()
        // AWT resources are absent in headless CI; bringWindowToFront() and tryInstallTrayIcon()
        // both degrade to no-ops when window == null / SystemTray.isSupported() == false.
    }
}
