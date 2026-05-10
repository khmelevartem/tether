package com.tubetoast.tether.network

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable

internal const val UPLOAD_BUFFER_SIZE = 64 * 1024

internal interface UploadStorage {
    fun ensureRoot()

    fun resolveDestination(fileName: String): String

    suspend fun writeBody(body: ByteReadChannel, destination: String): Long

    fun deleteIfExists(destination: String)

    fun logInfo(message: String)

    fun logError(message: String)
}

internal fun Application.installFileServerRoutes(storage: UploadStorage) {
    install(ContentNegotiation) { json() }
    routing {
        get("/health") { call.respond(HttpStatusCode.OK, "Tether OK") }
        post("/upload") {
            val rawName = call.request.queryParameters["name"]
            if (rawName.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "missing 'name' query parameter"),
                )
                return@post
            }
            val fileName = stripPathComponents(rawName)
            val destination = storage.resolveDestination(fileName)
            var uploadComplete = false
            try {
                val body = call.receiveChannel()
                val bytesWritten = storage.writeBody(body, destination)
                // Ktor closes the body channel silently when the client disconnects
                // mid-stream. closedCause covers exceptional close; the Content-Length
                // comparison covers clean close on incomplete bodies.
                body.closedCause?.let { throw it }
                val expected = call.request.contentLength()
                if (expected != null && bytesWritten < expected) {
                    error("FileServer: incomplete upload — got $bytesWritten of $expected bytes")
                }
                uploadComplete = true
                storage.logInfo("received '$fileName' — $bytesWritten bytes → $destination")
                call.respond(HttpStatusCode.OK, mapOf("savedPath" to destination))
            } catch (e: Exception) {
                storage.logError("upload failed for '$fileName' — ${e.message ?: "unknown error"}")
                try {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (e.message ?: "upload failed")),
                    )
                } catch (_: Exception) {
                }
            } finally {
                if (!uploadComplete) storage.deleteIfExists(destination)
            }
        }
    }
}

internal suspend fun streamUploadBody(
    input: ByteReadChannel,
    write: (buffer: ByteArray, length: Int) -> Unit,
) {
    val buffer = ByteArray(UPLOAD_BUFFER_SIZE)
    while (!input.isClosedForRead) {
        val n = input.readAvailable(buffer, 0, buffer.size)
        if (n <= 0) break
        write(buffer, n)
    }
}

private fun stripPathComponents(raw: String): String =
    raw.substringAfterLast('/').substringAfterLast('\\')
