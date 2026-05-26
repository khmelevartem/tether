package com.tubetoast.tether.transfer

import kotlinx.coroutines.flow.Flow

interface ConnectionMonitor {
    /** Emits Unit whenever the connection to the peer drops. */
    val drops: Flow<Unit>

    /** Suspends until the peer reconnects. Returns true if reconnected, false if timed out. */
    suspend fun awaitReconnect(timeout: kotlin.time.Duration = ReconnectionTimeout.DEFAULT): Boolean
}
