package com.tubetoast.tether.transfer

import com.tubetoast.tether.util.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

class UnreadableSourceException(
    val sourceName: String,
    cause: Throwable? = null,
) : RuntimeException(cause)

class ReceiverWriteFailedException(
    val httpStatus: Int? = null,
    cause: Throwable? = null,
) : RuntimeException(cause)

class PeerUnreachableException(
    cause: Throwable? = null,
) : RuntimeException(cause)

/**
 * Drives a single outbound batch to one peer: walks the source list, invokes `sendOne` per
 * file, emits `BatchProgress` updates, returns the final `BatchOutcome`. Owns wire-level
 * concerns within that one batch — per-file cancel, drop-and-reconnect within the
 * reconnection timeout, progress throttling. One instance per batch attempt.
 */
class BatchSender(
    private val sendOne: suspend (FileSource, onProgress: (Long, Long?) -> Unit) -> Unit,
    private val connectionMonitor: ConnectionMonitor,
    private val reconnectionTimeout: Duration = ReconnectionTimeout.DEFAULT,
    private val progressThrottle: Duration = 100.milliseconds,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val dispatcher: CoroutineDispatcher = IoDispatcher.limitedParallelism(1),
) {
    private var currentSendJob: Job? = null
    private var currentFileName: String? = null
    private var cancelledGeneration: Long = -1L
    private var currentGeneration: Long = 0L

    suspend fun cancelCurrent(name: String): Boolean = withContext(dispatcher) {
        if (currentFileName != name) return@withContext false
        cancelledGeneration = currentGeneration
        currentSendJob?.cancel()
        true
    }

    suspend fun run(
        sources: List<FileSource>,
        skipPredicate: (FileSource) -> Boolean = { false },
        emit: suspend (BatchProgress) -> Unit,
    ): BatchOutcome = withContext(dispatcher) {
        if (sources.isEmpty()) return@withContext BatchOutcome.AllSent

        val perFile: MutableList<PerFileStatus> = sources
            .map { PerFileStatus.Queued(it.name, it.sizeBytes) }
            .toMutableList()
        var sentBytes = 0L
        val totalBytes: Long? = sources.fold(0L as Long?) { acc, src ->
            val size = src.sizeBytes
            if (acc != null && size != null) acc + size else null
        }

        try {
            var i = 0
            while (i < sources.size) {
                val src = sources[i]

                if (skipPredicate(src)) {
                    perFile[i] = PerFileStatus.Failed(
                        src.name,
                        src.sizeBytes,
                        FailureReason.CancelledByUser,
                        cancelledByUser = true,
                    )
                    i++
                    continue
                }

                perFile[i] = PerFileStatus.InProgress(src.name, src.sizeBytes, 0L)

                val rate = BytesPerSecondMovingAverage(timeSource = timeSource)
                var bytesDone = 0L
                var dropDetected = false
                var lastSending: BatchProgress.Sending =
                    buildSending(i, sources, sentBytes, totalBytes, null, perFile)
                emit(lastSending)

                val fileGeneration: Long = ++currentGeneration
                currentFileName = src.name
                var capturedSendJob: Job? = null
                val perFileFailure: FailureReason? = try {
                    coroutineScope {
                        val sendJob = launch {
                            sendOne(src) { done, _ ->
                                val delta = done - bytesDone
                                bytesDone = done
                                sentBytes += delta
                                rate.record(bytesDone)
                                perFile[i] = PerFileStatus.InProgress(src.name, src.sizeBytes, bytesDone)
                            }
                        }
                        capturedSendJob = sendJob
                        currentSendJob = sendJob
                        val progressJob = launch {
                            while (true) {
                                delay(progressThrottle)
                                ensureActive()
                                val st =
                                    buildSending(i, sources, sentBytes, totalBytes, rate.valueOrNull(), perFile)
                                lastSending = st
                                emit(st)
                            }
                        }
                        val dropJob = launch {
                            connectionMonitor.drops.collect {
                                dropDetected = true
                                sendJob.cancel()
                            }
                        }
                        sendJob.join()
                        progressJob.cancel()
                        dropJob.cancel()
                    }
                    val wasCancelled = cancelledGeneration == fileGeneration && capturedSendJob?.isCancelled == true
                    if (wasCancelled) FailureReason.CancelledByUser else null
                } catch (e: UnreadableSourceException) {
                    FailureReason.Unreadable(e.sourceName)
                } catch (e: ReceiverWriteFailedException) {
                    FailureReason.ReceiverWriteFailed(e.httpStatus)
                } catch (e: PeerUnreachableException) {
                    FailureReason.PeerUnreachable
                } catch (e: CancellationException) {
                    if (!dropDetected) throw e
                    null
                } finally {
                    currentSendJob = null
                    currentFileName = null
                }

                if (perFileFailure != null) {
                    val cancelledByUser = perFileFailure is FailureReason.CancelledByUser
                    perFile[i] = PerFileStatus.Failed(src.name, src.sizeBytes, perFileFailure, cancelledByUser)
                    sentBytes -= bytesDone
                    i++
                    continue
                }

                if (dropDetected) {
                    emit(
                        BatchProgress.Reconnecting(
                            remainingSeconds = reconnectionTimeout.inWholeSeconds.toInt(),
                            snapshotBeforeDrop = lastSending,
                        ),
                    )
                    if (connectionMonitor.awaitReconnect(reconnectionTimeout)) {
                        sentBytes -= bytesDone
                        perFile[i] = PerFileStatus.Queued(src.name, src.sizeBytes)
                        continue
                    } else {
                        for (j in i until sources.size) {
                            perFile[j] =
                                PerFileStatus.Failed(sources[j].name, sources[j].sizeBytes, FailureReason.NetworkLost)
                        }
                        val outcome = BatchOutcome.Failed(
                            TransferErrorReason.NetworkLost,
                            perFile.count { it is PerFileStatus.Done },
                        )
                        emit(BatchProgress.Completed(outcome, perFile.toList()))
                        return@withContext outcome
                    }
                }

                perFile[i] = PerFileStatus.Done(src.name, src.sizeBytes)
                i++
            }
        } catch (e: CancellationException) {
            val doneCount = perFile.count { it is PerFileStatus.Done }
            val remaining = mutableListOf<String>()
            for (j in perFile.indices) {
                val st = perFile[j]
                if (st is PerFileStatus.Queued || st is PerFileStatus.InProgress) {
                    remaining.add(st.name)
                    perFile[j] = PerFileStatus.Failed(st.name, st.size, FailureReason.TransferCancelled)
                }
            }
            val outcome = BatchOutcome.Cancelled(doneCount, remaining)
            emit(BatchProgress.Completed(outcome, perFile.toList()))
            return@withContext outcome
        }

        return@withContext finalizeOutcome(perFile, emit)
    }

    private fun buildSending(
        index: Int,
        sources: List<FileSource>,
        sentBytes: Long,
        totalBytes: Long?,
        bytesPerSec: Long?,
        perFile: List<PerFileStatus>,
    ): BatchProgress.Sending = BatchProgress.Sending(
        currentFile = sources[index].name,
        currentIndex = index,
        totalFiles = sources.size,
        sentBytes = sentBytes,
        totalBytes = totalBytes,
        bytesPerSec = bytesPerSec,
        skippedCount = perFile.count { it is PerFileStatus.Failed },
        perFile = perFile.toList(),
    )

    private suspend fun finalizeOutcome(
        perFile: MutableList<PerFileStatus>,
        emit: suspend (BatchProgress) -> Unit,
    ): BatchOutcome {
        val doneCount = perFile.count { it is PerFileStatus.Done }
        val failedEntries = perFile.filterIsInstance<PerFileStatus.Failed>()
        val failedNames = failedEntries.map { it.name }

        if (failedNames.isEmpty()) {
            val outcome = BatchOutcome.AllSent
            emit(BatchProgress.Completed(outcome, perFile.toList()))
            return outcome
        }

        val allCancelledByUser = failedEntries.all { it.cancelledByUser }
        if (doneCount == 0 && !allCancelledByUser) {
            val errorReason = failedEntries.dominantErrorReason()
            val outcome = BatchOutcome.Failed(errorReason, 0)
            emit(BatchProgress.Completed(outcome, perFile.toList()))
            return outcome
        }

        val outcome = BatchOutcome.PartialSent(failedNames)
        emit(BatchProgress.Completed(outcome, perFile.toList()))
        return outcome
    }

    private fun List<PerFileStatus.Failed>.dominantErrorReason(): TransferErrorReason = when {
        all { it.reason is FailureReason.ReceiverWriteFailed } -> TransferErrorReason.ReceiverWriteFailed
        all { it.reason is FailureReason.PeerUnreachable } -> TransferErrorReason.PeerUnreachable
        else -> TransferErrorReason.AllFilesFailed
    }
}

private class BytesPerSecondMovingAverage(
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val smoothing = 0.3
    private var current: Double? = null
    private var lastBytes: Long = 0L
    private var lastMark = timeSource.markNow()

    fun record(bytesNow: Long) {
        val elapsed = lastMark.elapsedNow()
        if (elapsed.inWholeMilliseconds <= 0) return
        val elapsedSec = elapsed.toDouble(DurationUnit.SECONDS).coerceAtLeast(0.001)
        val sample = (bytesNow - lastBytes).toDouble() / elapsedSec
        current = current?.let { it * (1 - smoothing) + sample * smoothing } ?: sample
        lastBytes = bytesNow
        lastMark = timeSource.markNow()
    }

    fun valueOrNull(): Long? = current?.toLong()
}
