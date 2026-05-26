package com.tubetoast.tether.transfer

import kotlinx.coroutines.flow.Flow

data object ConnectionDrop

interface ConnectionMonitor {
    val drops: Flow<ConnectionDrop>

    /** Returns true if reconnected within `timeout`, false if timed out. */
    suspend fun awaitReconnect(timeout: kotlin.time.Duration = ReconnectionTimeout.DEFAULT): Boolean
}
