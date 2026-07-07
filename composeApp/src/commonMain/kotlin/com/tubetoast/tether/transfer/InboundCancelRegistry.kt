@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.tubetoast.tether.transfer

import com.tubetoast.tether.protocol.PeerIdentity
import kotlinx.coroutines.CompletableDeferred
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.wrapper.withTag
import kotlin.concurrent.atomics.AtomicReference

/**
 * Vends a fresh one-shot cancel signal per in-flight inbound upload. A signal handed out by
 * [signalFor] belongs only to the upload that requested it — the fresh-deferred-per-upload
 * structure means a cancel that arrives after that upload has already ended has no later
 * transfer to abort, so no explicit staleness cleanup is needed.
 *
 * The signal map is keyed by peer, one entry per peer, replaced on every [signalFor] call — two
 * concurrent uploads from the same peer would let a cancel reach only the most recently
 * registered one. Not reachable today since uploads from one peer are sequential; revisit for
 * #225. The map itself grows unbounded with the number of distinct peers seen this process
 * lifetime, never shrinking.
 */
class InboundCancelRegistry {
    private val log = KydraLog.withTag(default = "InboundCancelRegistry")

    private val signals = AtomicReference(emptyMap<PeerIdentity, CompletableDeferred<Unit>>())

    /** Registers a fresh signal for the peer's current upload, replacing any signal left by a previous one. */
    fun signalFor(peer: PeerIdentity): CompletableDeferred<Unit> {
        val fresh = CompletableDeferred<Unit>()
        while (true) {
            val current = signals.load()
            if (signals.compareAndSet(current, current + (peer to fresh))) return fresh
        }
    }

    /** Completes the peer's current signal, if one is registered. No-op if the peer has no in-flight upload. */
    fun request(peer: PeerIdentity) {
        signals.load()[peer]?.complete(Unit)
        log.info { "cancel requested for peer ${peer.id}" }
    }
}
