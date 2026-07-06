package com.tubetoast.tether.network

import com.tubetoast.tether.protocol.PeerIdentity
import com.tubetoast.tether.transfer.InboundEvent
import kotlinx.coroutines.flow.SharedFlow

expect class FileServer {
    /** The OS-assigned port after [start] is called; -1 before start. */
    val port: Int

    val events: SharedFlow<InboundEvent>

    fun start(): Int

    fun stop()

    suspend fun cancelInbound(peer: PeerIdentity)
}
