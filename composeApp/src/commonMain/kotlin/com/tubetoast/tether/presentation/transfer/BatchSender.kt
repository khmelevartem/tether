package com.tubetoast.tether.presentation.transfer

import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.SendResult
import com.tubetoast.tether.transfer.FileSource
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.time.TimeSource

private const val EMA_ALPHA = 0.3

private val referenceTime = TimeSource.Monotonic.markNow()

private fun epochMillis(): Long = referenceTime.elapsedNow().inWholeMilliseconds

class BatchSender(
    private val sendOne: suspend (
        device: Device,
        channel: ByteReadChannel,
        name: String,
        size: Long?,
        onProgress: (Long, Long?) -> Unit,
    ) -> SendResult,
    private val clock: () -> Long = ::epochMillis,
) {
    suspend fun run(
        peer: Device,
        sources: List<FileSource>,
        onState: (TransferState) -> Unit,
    ): BatchOutcome {
        val failed = mutableListOf<FailedFile>()
        var sent = 0
        var cumulativeBytesDone = 0L
        val totalBytes = sources.mapNotNull { it.size }.takeIf { it.size == sources.size }?.sum()
        var speedEma = 0L
        var lastProgressTime = clock()
        var lastProgressBytes = 0L

        for (source in sources) {
            currentCoroutineContext().ensureActive()
            val wireName = source.relativePath ?: source.name
            val channel = try {
                source.openReadChannel()
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                failed += FailedFile(source.name, FailureReason.Unreadable)
                val notice = "Couldn't read ${source.name} — skipping."
                val currentState = TransferState.InProgress(
                    currentFile = source.name,
                    bytesDone = cumulativeBytesDone,
                    bytesTotal = totalBytes,
                    speedBytesPerSec = speedEma,
                    inlineNotice = notice,
                )
                onState(currentState)
                continue
            }

            var fileBytesDone = 0L
            val result = sendOne(peer, channel, wireName, source.size) { fileBytes, _ ->
                fileBytesDone = fileBytes
                val now = clock()
                val elapsed = (now - lastProgressTime).coerceAtLeast(1)
                val delta = (cumulativeBytesDone + fileBytes) - lastProgressBytes
                if (elapsed >= 500) {
                    val instantSpeed = delta * 1000L / elapsed
                    speedEma = if (speedEma == 0L) {
                        instantSpeed
                    } else {
                        (EMA_ALPHA * instantSpeed + (1 - EMA_ALPHA) * speedEma).toLong()
                    }
                    lastProgressTime = now
                    lastProgressBytes = cumulativeBytesDone + fileBytes
                }
                onState(
                    TransferState.InProgress(
                        currentFile = source.name,
                        bytesDone = cumulativeBytesDone + fileBytes,
                        bytesTotal = totalBytes,
                        speedBytesPerSec = speedEma,
                    ),
                )
            }

            currentCoroutineContext().ensureActive()

            when (result) {
                is SendResult.Success -> {
                    sent++
                    cumulativeBytesDone += fileBytesDone
                }

                is SendResult.Failure -> {
                    val reason = when {
                        result.reason.contains("connect", ignoreCase = true) ||
                            result.reason.contains("socket", ignoreCase = true) ||
                            result.reason.contains("network", ignoreCase = true) ->
                            FailureReason.ConnectionLost

                        else -> FailureReason.ReceiverWriteFailed
                    }
                    if (reason == FailureReason.ConnectionLost) {
                        return BatchOutcome(
                            sent = sent,
                            total = sources.size,
                            failed = failed + FailedFile(source.name, reason) +
                                sources.drop(sources.indexOf(source) + 1).map { s ->
                                    FailedFile(s.name, FailureReason.ConnectionLost)
                                },
                            connectionLostMidway = true,
                        )
                    }
                    failed += FailedFile(source.name, reason)
                    val notice = "Couldn't save ${source.name} on ${peer.name}."
                    onState(
                        TransferState.InProgress(
                            currentFile = source.name,
                            bytesDone = cumulativeBytesDone,
                            bytesTotal = totalBytes,
                            speedBytesPerSec = speedEma,
                            inlineNotice = notice,
                        ),
                    )
                }
            }
        }

        return BatchOutcome(
            sent = sent,
            total = sources.size,
            failed = failed,
            connectionLostMidway = false,
        )
    }
}
