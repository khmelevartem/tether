package com.tubetoast.tether.network

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveStream
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import java.io.File

private const val BUFFER_SIZE = 64 * 1024

actual class FileServer(
    private val port: Int,
    private val downloadsDir: File = File(System.getProperty("user.home"), "Downloads/Tether"),
) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    actual fun start(): Int {
        check(server == null) { "FileServer is already running" }
        downloadsDir.mkdirs()
        val srv = embeddedServer(CIO, port = port) {
            install(ContentNegotiation) { json() }
            routing {
                get("/health") { call.respond(HttpStatusCode.OK, "Tether OK") }
                post("/upload") {
                    val rawName = call.request.queryParameters["name"]
                    if (rawName.isNullOrBlank()) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing 'name' query parameter"))
                        return@post
                    }
                    // Strip path components to prevent traversal attacks
                    val fileName = File(rawName).name
                    val dest = resolveDestination(downloadsDir, fileName)
                    var uploadComplete = false
                    try {
                        call.receiveStream().use { input ->
                            dest.outputStream().use { output ->
                                input.copyTo(output, bufferSize = BUFFER_SIZE)
                            }
                        }
                        uploadComplete = true
                        call.respond(HttpStatusCode.OK, mapOf("savedPath" to dest.absolutePath))
                    } catch (e: Exception) {
                        System.err.println("ERROR: upload failed for '$fileName' — ${e.message}")
                        try {
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to (e.message ?: "upload failed")),
                            )
                        } catch (_: Exception) {
                        }
                    } finally {
                        if (!uploadComplete) {
                            try {
                                dest.delete()
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            }
        }.start(wait = false)
        server = srv
        // resolvedConnectors() returns the actual OS-assigned port when port=0 was specified,
        // eliminating the TOCTOU race that would exist if we probed with ServerSocket(0) first.
        return runBlocking { srv.engine.resolvedConnectors() }.first().port
    }

    actual fun stop() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 1_000)
        server = null
    }
}

private fun resolveDestination(dir: File, fileName: String): File {
    var dest = File(dir, fileName)
    if (!dest.exists()) return dest
    val ext = fileName.substringAfterLast('.', "")
    val base = if (ext.isEmpty()) fileName else fileName.removeSuffix(".$ext")
    var i = 1
    do {
        dest = File(dir, if (ext.isEmpty()) "${base}_$i" else "${base}_$i.$ext")
        i++
    } while (dest.exists())
    return dest
}
