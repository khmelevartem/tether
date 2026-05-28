package com.tubetoast.tether.di

import com.tubetoast.tether.transfer.ConnectionDrop
import com.tubetoast.tether.transfer.ConnectionMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.time.Duration

internal object NoOpConnectionMonitor : ConnectionMonitor {
    override val drops: Flow<ConnectionDrop> = MutableSharedFlow()

    override suspend fun awaitReconnect(timeout: Duration): Boolean = false
}
