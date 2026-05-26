package com.tubetoast.tether.transfer

import kotlinx.coroutines.flow.Flow

data object ConnectionDrop

interface ConnectionMonitor {
    val drops: Flow<ConnectionDrop>

    /** Suspends until the peer reconnects. Returns true if reconnected, false if timed out. */
    suspend fun awaitReconnect(timeout: kotlin.time.Duration = ReconnectionTimeout.DEFAULT): Boolean
}
