package com.tubetoast.tether.network

import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.InfoDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.error
import ru.pocketbyte.kydra.log.wrapper.withTag

private val log = KydraLog.withTag(default = "RendezvousClient")

open class RendezvousClient(
    private val client: HttpClient,
) {
    companion object {
        fun default(): RendezvousClient = RendezvousClient(
            HttpClient(CIO) {
                install(ContentNegotiation) { json() }
            },
        )
    }

    open suspend fun sendHello(target: Device, ownInfo: InfoDto): Boolean = try {
        val response = client.post("http://${target.host}:${target.port}/hello") {
            contentType(ContentType.Application.Json)
            setBody(ownInfo)
        }
        response.status.isSuccess()
    } catch (e: Exception) {
        log.error { "hello failed → ${target.host}:${target.port} — ${e.message}" }
        false
    }
}
