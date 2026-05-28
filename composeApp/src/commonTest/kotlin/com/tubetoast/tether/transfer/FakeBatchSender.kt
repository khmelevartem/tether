package com.tubetoast.tether.transfer

import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration.Companion.milliseconds

internal fun fakeBatchSender(): () -> BatchSender = {
    BatchSender(
        sendOne = { src, onProgress ->
            onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
        },
        connectionMonitor = FakeConnectionMonitor(),
        progressThrottle = 100.milliseconds,
        dispatcher = Dispatchers.Unconfined,
    )
}
