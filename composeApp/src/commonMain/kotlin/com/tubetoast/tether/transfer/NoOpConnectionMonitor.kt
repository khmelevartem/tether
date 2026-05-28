package com.tubetoast.tether.transfer

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.time.Duration

internal object NoOpConnectionMonitor : ConnectionMonitor {
    override val drops: Flow<ConnectionDrop> = MutableSharedFlow()

    override suspend fun awaitReconnect(timeout: Duration): Boolean = false
}
