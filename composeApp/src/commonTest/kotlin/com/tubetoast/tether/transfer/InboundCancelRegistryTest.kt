package com.tubetoast.tether.transfer

import com.tubetoast.tether.protocol.PeerIdentity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InboundCancelRegistryTest {
    private val peerA = PeerIdentity("peer-a")
    private val peerB = PeerIdentity("peer-b")

    @Test
    fun `request with no registered signal is a silent no-op`() = runTest {
        val registry = InboundCancelRegistry()

        registry.request(peerA)
    }

    @Test
    fun `signal replaced by a later signalFor is not completed on request`() = runTest {
        val registry = InboundCancelRegistry()
        val stale = registry.signalFor(peerA)
        val fresh = registry.signalFor(peerA)

        registry.request(peerA)

        assertFalse(stale.isCompleted)
        assertTrue(fresh.isCompleted)
    }

    @Test
    fun `request completes the current signalFor deferred`() = runTest {
        val registry = InboundCancelRegistry()
        val signal = registry.signalFor(peerA)

        registry.request(peerA)

        assertTrue(signal.isCompleted)
        signal.await()
    }

    @Test
    fun `signals for different peers are independent`() = runTest {
        val registry = InboundCancelRegistry()
        val signalA = registry.signalFor(peerA)
        val signalB = registry.signalFor(peerB)

        registry.request(peerA)

        assertTrue(signalA.isCompleted)
        assertFalse(signalB.isCompleted)
    }
}
