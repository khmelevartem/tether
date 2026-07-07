package com.tubetoast.tether.transfer

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object ReconnectionTimeout {
    val DEFAULT: Duration = 15.seconds
}

/**
 * Longest silence tolerated on an inbound transfer while the connection still looks healthy
 * (no [ReceiveEvent.ConnectionLost] observed) before the engine treats it as dead. A lazily
 * materializing sender source (e.g. an iOS photo export) can delay the request for the next
 * file well past its read, so the grace is aligned with Ktor CIO's connection idle timeout
 * (45s) rather than a tighter bound: a truly dead TCP connection times out at the transport
 * layer first, and this grace only catches the case where the last wire event (`/batch-end`)
 * never arrives.
 */
val INBOUND_IDLE_GRACE: Duration = 45.seconds
