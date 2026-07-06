package com.tubetoast.tether.transfer

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Carries [InboundEvent]s from the file-server routes to [InboundEventRouter]. One bus, two role
 * views: routes call [emit], the router reads [events].
 */
class InboundEventBus {
    private val mutableEvents = MutableSharedFlow<InboundEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<InboundEvent> = mutableEvents.asSharedFlow()

    fun emit(event: InboundEvent) {
        mutableEvents.tryEmit(event)
    }
}
