package com.tubetoast.tether.network

import com.tubetoast.tether.protocol.Device
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

class FileClient : kotlin.io.Closeable {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
    }

    suspend fun ping(device: Device): Boolean {
        // TODO: GET http://${device.host}:${device.port}/health
        throw NotImplementedError("ping() is not yet implemented")
    }

    override fun close() {
        client.close()
    }
}
