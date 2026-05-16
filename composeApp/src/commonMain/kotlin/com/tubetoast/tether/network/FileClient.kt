package com.tubetoast.tether.network

import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.SendResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.Closeable
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class FileClient(
    private val client: HttpClient,
    private val noProgressTimeout: Duration = DEFAULT_NO_PROGRESS_TIMEOUT,
) : Closeable {
    companion object {
        fun default(): FileClient = FileClient(
            client = HttpClient(CIO) {
                install(ContentNegotiation) { json() }
                install(HttpTimeout) {
                    requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                    socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                }
            },
        )
    }

    suspend fun ping(device: Device): Boolean {
        // TODO: GET http://${device.host}:${device.port}/health
        throw NotImplementedError("ping() is not yet implemented")
    }

    suspend fun send(
        device: Device,
        channel: ByteReadChannel,
        fileName: String,
        totalBytes: Long? = null,
        onProgress: ((bytesTransferred: Long, totalBytes: Long?) -> Unit)? = null,
    ): SendResult = try {
        sendInternal(device, channel, fileName, totalBytes, onProgress)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        SendResult.Failure(e.message ?: e::class.simpleName ?: "unknown error")
    }

    override fun close() {
        client.close()
    }

    private suspend fun sendInternal(
        device: Device,
        channel: ByteReadChannel,
        fileName: String,
        totalBytes: Long?,
        onProgress: ((Long, Long?) -> Unit)?,
    ): SendResult = coroutineScope {
        val bytesSent = MutableStateFlow(0L)
        val pipe = ByteChannel(autoFlush = true)
        val watchdog = launch {
            var lastBytesSent = 0L
            var stallTicks = 0
            val timeoutTicks = noProgressTimeout.inWholeSeconds.coerceAtLeast(1)
            while (true) {
                delay(1.seconds)
                val currentBytesSent = bytesSent.value
                if (currentBytesSent != lastBytesSent) {
                    lastBytesSent = currentBytesSent
                    stallTicks = 0
                } else if (lastBytesSent > 0L) {
                    stallTicks++
                    if (stallTicks >= timeoutTicks) {
                        error("no upload progress for $noProgressTimeout")
                    }
                }
            }
        }
        val copyJob = launch {
            try {
                copyWithProgress(channel, pipe, totalBytes) { sent, total ->
                    bytesSent.value = sent
                    onProgress?.invoke(sent, total)
                }
            } finally {
                watchdog.cancel()
            }
        }
        val result = doSend(device, pipe, fileName, totalBytes)
        copyJob.cancel()
        result
    }

    private suspend fun doSend(
        device: Device,
        channel: ByteReadChannel,
        fileName: String,
        totalBytes: Long?,
    ): SendResult = try {
        val response = client.post("http://${device.host}:${device.port}/upload") {
            parameter("name", fileName)
            setBody(channel.asOctetStreamContent(totalBytes))
        }
        if (response.status == HttpStatusCode.OK) {
            val body = response.body<Map<String, String>>()
            SendResult.Success(body["savedPath"] ?: "")
        } else {
            val body = response.body<Map<String, String>>()
            SendResult.Failure(body["error"] ?: response.status.description)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        SendResult.Failure(e.message ?: "unknown error")
    }
}

private val DEFAULT_NO_PROGRESS_TIMEOUT: Duration = 60.seconds

private fun ByteReadChannel.asOctetStreamContent(totalBytes: Long?): OutgoingContent =
    object : OutgoingContent.ReadChannelContent() {
        override val contentType: ContentType = ContentType.Application.OctetStream
        override val contentLength: Long? = totalBytes

        override fun readFrom(): ByteReadChannel = this@asOctetStreamContent
    }

private const val COPY_BUFFER_SIZE = 8 * 1024

private suspend fun copyWithProgress(
    source: ByteReadChannel,
    destination: ByteChannel,
    totalBytes: Long?,
    onProgress: (Long, Long?) -> Unit,
) {
    val buffer = ByteArray(COPY_BUFFER_SIZE)
    var transferred = 0L
    var cause: Throwable? = null
    try {
        while (!source.isClosedForRead) {
            val bytesRead = source.readAvailable(buffer)
            if (bytesRead > 0) {
                destination.writeFully(buffer, 0, bytesRead)
                transferred += bytesRead
                onProgress(transferred, totalBytes)
            }
        }
        cause = source.closedCause
    } catch (e: CancellationException) {
        cause = e
        throw e
    } catch (e: Throwable) {
        cause = e
    } finally {
        if (cause != null) destination.cancel(cause) else destination.close()
    }
}
