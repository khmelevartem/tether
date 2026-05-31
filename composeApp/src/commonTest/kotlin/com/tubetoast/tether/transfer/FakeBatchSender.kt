package com.tubetoast.tether.transfer

import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration.Companion.milliseconds

internal fun fakeBatchSender(
    sendOneOverride: (suspend (FileSource, (Long, Long?) -> Unit) -> Unit)? = null,
    pauseChannel: kotlinx.coroutines.channels.Channel<Unit>? = null,
): () -> BatchSender = {
    BatchSender(
        sendOne = sendOneOverride ?: { src, onProgress ->
            pauseChannel?.receive()
            onProgress(src.sizeBytes ?: 0L, src.sizeBytes)
        },
        connectionMonitor = FakeConnectionMonitor(),
        progressThrottle = 100.milliseconds,
        dispatcher = Dispatchers.Unconfined,
    )
}
