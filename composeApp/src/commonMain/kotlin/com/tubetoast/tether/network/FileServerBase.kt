@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.tubetoast.tether.network

import com.tubetoast.tether.transfer.InboundEvent
import com.tubetoast.tether.transfer.PeerIdentity
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.wrapper.withTag
import kotlin.concurrent.atomics.AtomicReference

/**
 * Holds the inbound event bus and atomic cancel state shared by all platform FileServer actuals.
 * Platform actuals extend this class and implement start/stop using their Ktor engine.
 * The [events] flow and [cancelInbound] are provided here; actuals declare them as [actual]
 * and delegate to [mutableEvents]/[doCancelInbound].
 */
abstract class FileServerBase {
    private val log = KydraLog.withTag(default = "FileServer")

    protected val mutableEvents = MutableSharedFlow<InboundEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    protected val eventsSharedFlow: SharedFlow<InboundEvent> = mutableEvents.asSharedFlow()

    private val cancelledSet = AtomicReference(emptySet<PeerIdentity>())

    protected val isCancelRequested: (PeerIdentity) -> Boolean = { peer ->
        peer in cancelledSet.load()
    }

    protected val onCancelConsumed: (PeerIdentity) -> Unit = { peer ->
        while (true) {
            val current = cancelledSet.load()
            if (cancelledSet.compareAndSet(current, current - peer)) break
        }
    }

    /** Clears any stale cancel flag for the peer so a previous cancel cannot abort a new transfer. */
    protected fun clearCancelFlag(peer: PeerIdentity) {
        while (true) {
            val current = cancelledSet.load()
            if (cancelledSet.compareAndSet(current, current - peer)) break
        }
    }

    protected fun doCancelInbound(peer: PeerIdentity) {
        while (true) {
            val current = cancelledSet.load()
            if (cancelledSet.compareAndSet(current, current + peer)) break
        }
        log.info { "cancel requested for peer ${peer.id}" }
    }
}
