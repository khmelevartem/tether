package com.tubetoast.tether.peer

import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.PeerIdentity

data class Peer(
    val id: PeerIdentity,
    val device: Device,
    val isOnline: Boolean = true,
)
