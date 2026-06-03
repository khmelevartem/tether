package com.tubetoast.tether.network

import com.tubetoast.tether.protocol.Device

/** Client-side gate run before an outbound transfer: ensures the peer is paired and may receive. */
fun interface PeerPairing {
    /** Returns true if the transfer to [device] may proceed (already trusted, or pairing just succeeded). */
    suspend fun ensurePaired(device: Device): Boolean
}

/** No pairing requirement — every peer is allowed. The stance of platforms without a confirmation UI yet. */
object NoPairing : PeerPairing {
    override suspend fun ensurePaired(device: Device): Boolean = true
}
