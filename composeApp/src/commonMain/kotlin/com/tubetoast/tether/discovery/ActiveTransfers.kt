package com.tubetoast.tether.discovery

import kotlinx.coroutines.flow.StateFlow

/** Fingerprints of peers with a file transfer in flight, inbound or outbound. */
interface ActiveTransfers {
    val peers: StateFlow<Set<String>>
}
