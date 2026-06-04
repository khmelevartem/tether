package com.tubetoast.tether.network

import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.identity.DeviceIdentityStore
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.PairRequest
import com.tubetoast.tether.protocol.PairResponse
import com.tubetoast.tether.protocol.PeerAnnouncement
import com.tubetoast.tether.security.TrustedDeviceStore
import com.tubetoast.tether.security.computePinCode
import com.tubetoast.tether.security.deviceIdFromPublicKey
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.request.contentLength
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.withTimeoutOrNull
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.debug
import ru.pocketbyte.kydra.log.error
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.wrapper.withTag

internal const val UPLOAD_BUFFER_SIZE = 64 * 1024

// How long the server holds the /pair response while waiting for the user to confirm the PIN.
internal const val DEFAULT_PAIRING_TIMEOUT_MS = 60_000L

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

private val log = KydraLog.withTag(default = "FileServerRoutes")

internal data class UploadHandle(
    val destination: String,
    /** Parent dirs created alongside this handle, ordered child→parent for deletion. */
    val createdDirs: List<String>,
)

internal interface UploadStorage {
    fun ensureRoot()

    fun resolveDestination(relativePath: String): UploadHandle

    suspend fun writeBody(body: ByteReadChannel, handle: UploadHandle): Long

    fun abort(handle: UploadHandle)
}

internal fun Application.installFileServerRoutes(
    storage: UploadStorage,
    trustedDeviceStore: TrustedDeviceStore,
    serverPublicKey: ByteArray,
    tracker: TransferActivityTracker = DefaultTransferActivityTracker(),
    deviceIdentityStore: DeviceIdentityStore? = null,
    discoveredDevicesStore: DiscoveredDevicesStore? = null,
    pairingConfirmationHandler: PairingConfirmationHandler? = null,
    pairingTimeoutMillis: Long = DEFAULT_PAIRING_TIMEOUT_MS,
) {
    install(ContentNegotiation) { json() }
    routing {
        get("/health") { call.respond(HttpStatusCode.OK, "Tether OK") }
        post("/hello") {
            val body = try {
                call.receive<PeerAnnouncement>()
            } catch (_: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid_body"))
                return@post
            }
            if (body.port !in 1..65535) {
                log.info { "hello rejected — invalid port ${body.port} from ${body.alias}" }
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid_port"))
                return@post
            }
            if (deviceIdentityStore != null && body.fingerprint == deviceIdentityStore.getOrCreate()) {
                log.debug { "hello self-suppressed (own fingerprint) from ${body.alias}" }
                call.respond(HttpStatusCode.OK, emptyMap<String, String>())
                return@post
            }
            val remoteHost = call.request.origin.remoteHost
            val knownName = discoveredDevicesStore?.nameFor(body.fingerprint)
            val device = Device(
                name = knownName ?: body.alias,
                host = remoteHost,
                port = body.port,
                fingerprint = body.fingerprint,
            )
            discoveredDevicesStore?.upsert(device)
            log.info { "hello from ${body.alias}@$remoteHost:${body.port}" }
            call.respond(HttpStatusCode.OK, emptyMap<String, String>())
        }
        post("/pair") {
            val request = try {
                call.receive<PairRequest>()
            } catch (_: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid_body"))
                return@post
            }
            val deviceId = deviceIdFromPublicKey(request.publicKey)
            if (!trustedDeviceStore.isTrusted(deviceId)) {
                if (pairingConfirmationHandler != null) {
                    val pin = computePinCode(serverPublicKey, request.publicKey)
                    val confirmed = withTimeoutOrNull(pairingTimeoutMillis) {
                        pairingConfirmationHandler.confirmPairing(pin, request.deviceName)
                    }
                    if (confirmed != true) {
                        log.info { "pairing rejected — device=${request.deviceName} confirmed=$confirmed" }
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "pairing_rejected"))
                        return@post
                    }
                }
            }
            // TODO(#361): server persists the peer key before the client user confirms — if the client
            // user later rejects (MITM detected), the server remains trusting. A two-phase commit
            // protocol (server defers save until client sends a follow-up commit) would fix this.
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
            log.info { "paired with ${request.deviceName}" }
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
            var handle: UploadHandle? = null
            try {
                val resolved = storage.resolveDestination(relativePath)
                handle = resolved
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
                    log.info { "received '$relativePath' — $bytesWritten bytes → ${resolved.destination}" }
                    call.respond(HttpStatusCode.OK, mapOf("savedPath" to resolved.destination))
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
                if (!uploadComplete) handle?.let { storage.abort(it) }
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
