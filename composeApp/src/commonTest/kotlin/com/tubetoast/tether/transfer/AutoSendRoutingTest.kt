package com.tubetoast.tether.transfer

import com.tubetoast.tether.transfer.PeerIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AutoSendRoutingTest {
    private val peerA = PeerIdentity("peer-a")
    private val peerB = PeerIdentity("peer-b")

    @Test
    fun `one online paired peer with autoSend enabled routes to AutoSend`() {
        val decision = AutoSendRouter.route(
            onlinePaired = listOf(peerA),
            autoSendEnabled = { true },
        )
        assertIs<RoutingDecision.AutoSend>(decision)
        assertEquals(peerA, decision.peer)
    }

    @Test
    fun `two online paired peers routes to RequirePeerListTap`() {
        val decision = AutoSendRouter.route(
            onlinePaired = listOf(peerA, peerB),
            autoSendEnabled = { true },
        )
        assertIs<RoutingDecision.RequirePeerListTap>(decision)
    }

    @Test
    fun `one online paired peer with autoSend disabled routes to RequirePeerListTap`() {
        val decision = AutoSendRouter.route(
            onlinePaired = listOf(peerA),
            autoSendEnabled = { false },
        )
        assertIs<RoutingDecision.RequirePeerListTap>(decision)
    }

    @Test
    fun `zero online paired peers routes to RequirePeerListTap`() {
        val decision = AutoSendRouter.route(
            onlinePaired = emptyList(),
            autoSendEnabled = { true },
        )
        assertIs<RoutingDecision.RequirePeerListTap>(decision)
    }
}
