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
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.Closeable

class FileClient : Closeable {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
    }

    suspend fun ping(device: Device): Boolean {
        // TODO: GET http://${device.host}:${device.port}/health
        throw NotImplementedError("ping() is not yet implemented")
    }

    suspend fun send(device: Device, channel: ByteReadChannel, fileName: String): SendResult = try {
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

    override fun close() {
        client.close()
    }
}
