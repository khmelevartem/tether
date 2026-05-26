package com.tubetoast.tether.transfer

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.time.Duration

internal class FakeConnectionMonitor : ConnectionMonitor {
    override val drops = MutableSharedFlow<ConnectionDrop>(extraBufferCapacity = 16)

    private val reconnectResult = MutableStateFlow<Boolean?>(null)

    suspend fun drop() {
        reconnectResult.value = null
        drops.emit(ConnectionDrop)
    }

    fun reconnect(success: Boolean) {
        reconnectResult.value = success
    }

    override suspend fun awaitReconnect(timeout: Duration): Boolean =
        reconnectResult.filterNotNull().first()
}
