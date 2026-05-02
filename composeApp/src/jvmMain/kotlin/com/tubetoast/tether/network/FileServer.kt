package com.tubetoast.tether.network

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking

class FileServer(
    private val port: Int,
) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    fun start(): Int {
        check(server == null) { "FileServer is already running" }
        val srv = embeddedServer(CIO, port = port) {
            install(ContentNegotiation) { json() }
            routing {
                get("/health") { call.respond(HttpStatusCode.OK, "Tether OK") }
                // TODO: POST /upload — receive file bytes from a peer
            }
        }.start(wait = false)
        server = srv
        // resolvedConnectors() returns the actual OS-assigned port when port=0 was specified,
        // eliminating the TOCTOU race that would exist if we probed with ServerSocket(0) first.
        return runBlocking { srv.resolvedConnectors() }.first().port
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 1_000)
        server = null
    }
}
