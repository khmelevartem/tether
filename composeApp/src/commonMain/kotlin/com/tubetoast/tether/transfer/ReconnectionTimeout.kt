package com.tubetoast.tether.transfer

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object ReconnectionTimeout {
    val DEFAULT: Duration = 15.seconds
}
