package com.tubetoast.tether.network

import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.SendResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.Closeable
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class FileClient : Closeable {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
    }

    suspend fun ping(device: Device): Boolean {
        // TODO: GET http://${device.host}:${device.port}/health
        throw NotImplementedError("ping() is not yet implemented")
    }

    suspend fun send(
        device: Device,
        channel: ByteReadChannel,
        fileName: String,
        totalBytes: Long = -1L,
        onProgress: ((bytesTransferred: Long, totalBytes: Long) -> Unit)? = null,
    ): SendResult = if (onProgress == null) {
        doSend(device, channel, fileName)
    } else {
        // Launch the copy into a pipe concurrently with the Ktor upload so that
        // Ktor reads from the pipe while we are filling it. coroutineScope waits
        // for the copy job after the Ktor call returns.
        coroutineScope {
            val pipe = ByteChannel(autoFlush = true)
            launch { copyWithProgress(channel, pipe, totalBytes, onProgress) }
            doSend(device, pipe, fileName)
        }
    }

    override fun close() {
        client.close()
    }

    private suspend fun doSend(
        device: Device,
        channel: ByteReadChannel,
        fileName: String,
    ): SendResult = try {
        val response = client.post("http://${device.host}:${device.port}/upload") {
            parameter("name", fileName)
            contentType(ContentType.Application.OctetStream)
            setBody(channel)
        }
        if (response.status == HttpStatusCode.OK) {
            val body = response.body<Map<String, String>>()
            SendResult.Success(body["savedPath"] ?: "")
        } else {
            val body = response.body<Map<String, String>>()
            SendResult.Failure(body["error"] ?: response.status.description)
        }
    } catch (e: Exception) {
        SendResult.Failure(e.message ?: "unknown error")
    }
}

private const val COPY_BUFFER_SIZE = 8 * 1024

private suspend fun copyWithProgress(
    source: ByteReadChannel,
    dest: ByteChannel,
    totalBytes: Long,
    onProgress: (Long, Long) -> Unit,
) {
    val buf = ByteArray(COPY_BUFFER_SIZE)
    var transferred = 0L
    try {
        while (!source.isClosedForRead) {
            val read = source.readAvailable(buf)
            if (read > 0) {
                dest.writeFully(buf, 0, read)
                transferred += read
                onProgress(transferred, totalBytes)
            }
        }
    } finally {
        dest.close()
    }
}
