package com.tubetoast.tether.discovery

import com.tubetoast.tether.protocol.PeerIdentity
import kotlinx.coroutines.flow.StateFlow

/** Identities of peers with a file transfer in flight, inbound or outbound. */
interface ActiveTransfers {
    val peers: StateFlow<Set<PeerIdentity>>
}
