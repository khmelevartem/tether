package com.tubetoast.tether.network

import com.tubetoast.tether.protocol.PairRequest
import com.tubetoast.tether.protocol.PairResponse
import com.tubetoast.tether.security.TrustedDeviceStore
import com.tubetoast.tether.security.deviceIdFromPublicKey
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.contentLength
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.error
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.wrapper.withTag

internal const val UPLOAD_BUFFER_SIZE = 64 * 1024

internal fun dedupFilename(leafName: String, exists: (candidate: String) -> Boolean): String {
    if (!exists(leafName)) return leafName
    // A leading dot marks a hidden file, not an extension separator.
    val dotIndex = leafName.lastIndexOf('.')
    val hasExt = dotIndex > 0
    val ext = if (hasExt) leafName.substring(dotIndex + 1) else ""
    val base = if (hasExt) leafName.substring(0, dotIndex) else leafName
    var i = 1
    while (true) {
        val candidate = if (hasExt) "${base}_$i.$ext" else "${base}_$i"
        if (!exists(candidate)) return candidate
        i++
    }
}

private val log = KydraLog.withTag(default = "Tether.FileServerRoutes")

internal interface UploadStorage {
    fun ensureRoot()

    fun resolveDestination(relativePath: String): String

    suspend fun writeBody(body: ByteReadChannel, destination: String): Long

    /** Deletes any partial destination file and removes empty parent directories up to root. */
    fun abort(destination: String)
}

internal fun Application.installFileServerRoutes(
    storage: UploadStorage,
    trustedDeviceStore: TrustedDeviceStore,
    serverPublicKey: ByteArray,
    tracker: TransferActivityTracker = DefaultTransferActivityTracker(),
) {
    install(ContentNegotiation) { json() }
    routing {
        get("/health") { call.respond(HttpStatusCode.OK, "Tether OK") }
        post("/pair") {
            val request = call.receive<PairRequest>()
            val deviceId = deviceIdFromPublicKey(request.publicKey)
            try {
                trustedDeviceStore.saveTrustedKey(deviceId, request.publicKey)
            } catch (e: Exception) {
                // Explicit 500 instead of relying on Ktor's default exception handler:
                // a silent 200 on persistence failure would tell the peer we trust them while we don't.
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "failed to persist trusted device")),
                )
                return@post
            }
            call.respond(HttpStatusCode.OK, PairResponse(publicKey = serverPublicKey))
        }
        post("/upload") {
            val rawName = call.request.rawQueryParameters["name"]
            val relativePath = rawName?.let { PathSanitization.sanitizeRelativePath(it) }
            if (relativePath == null) {
                log.info { "rejected upload — invalid_relative_path" }
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "invalid_relative_path"),
                )
                return@post
            }
            var uploadComplete = false
            var destination: String? = null
            try {
                val resolved = storage.resolveDestination(relativePath)
                destination = resolved
                tracker.withActiveTransfer {
                    val body = call.receiveChannel()
                    val bytesWritten = storage.writeBody(body, resolved)
                    // Ktor closes the body channel silently when the client disconnects
                    // mid-stream. closedCause covers exceptional close; the Content-Length
                    // comparison covers clean close on incomplete bodies.
                    body.closedCause?.let { throw it }
                    val expected = call.request.contentLength()
                    if (expected != null && bytesWritten < expected) {
                        error("FileServer: incomplete upload — got $bytesWritten of $expected bytes")
                    }
                    uploadComplete = true
                    log.info { "received '$relativePath' — $bytesWritten bytes → $resolved" }
                    call.respond(HttpStatusCode.OK, mapOf("savedPath" to resolved))
                }
            } catch (e: Exception) {
                log.error { "upload failed for '$relativePath' — ${e.message ?: "unknown error"}" }
                try {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (e.message ?: "upload failed")),
                    )
                } catch (_: Exception) {
                }
            } finally {
                if (!uploadComplete) destination?.let { storage.abort(it) }
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
