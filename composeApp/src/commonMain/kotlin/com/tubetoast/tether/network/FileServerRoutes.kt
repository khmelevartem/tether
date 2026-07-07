package com.tubetoast.tether.network

import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.identity.DeviceIdentityStore
import com.tubetoast.tether.protocol.BatchBeginRequest
import com.tubetoast.tether.protocol.BatchCancelRequest
import com.tubetoast.tether.protocol.BatchEndRequest
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.protocol.PairRequest
import com.tubetoast.tether.protocol.PairResponse
import com.tubetoast.tether.protocol.PeerAnnouncement
import com.tubetoast.tether.protocol.PeerIdentity
import com.tubetoast.tether.protocol.TetherRoutes
import com.tubetoast.tether.security.TrustedDeviceStore
import com.tubetoast.tether.security.deviceIdFromPublicKey
import com.tubetoast.tether.transfer.InboundCancelRegistry
import com.tubetoast.tether.transfer.InboundEvent
import com.tubetoast.tether.transfer.InboundEventBus
import com.tubetoast.tether.transfer.NoOpTransferActivityTracker
import com.tubetoast.tether.transfer.TransferActivityTracker
import com.tubetoast.tether.transfer.toPeerIdentity
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.debug
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

private val log = KydraLog.withTag(default = "FileServerRoutes")

private fun ApplicationCall.resolvePeer(discoveredDevicesStore: DiscoveredDevicesStore?): PeerIdentity? {
    val remoteHost = request.origin.remoteAddress
    return discoveredDevicesStore
        ?.devices
        ?.value
        ?.firstOrNull { it.host == remoteHost }
        ?.toPeerIdentity()
}

internal data class UploadHandle(
    val destination: String,
    /** Parent dirs created alongside this handle, ordered child→parent for deletion. */
    val createdDirs: List<String>,
)

internal interface UploadStorage {
    fun ensureRoot()

    fun resolveDestination(relativePath: String): UploadHandle

    suspend fun writeBody(body: ByteReadChannel, handle: UploadHandle): Long

    /**
     * Called exactly once on the success path, after the route confirms the body is complete
     * and decides not to abort. Mutually exclusive with [abort] for a given handle. Default no-op.
     */
    fun commit(handle: UploadHandle) = Unit

    fun abort(handle: UploadHandle)
}

internal fun Application.installFileServerRoutes(
    storage: UploadStorage,
    trustedDeviceStore: TrustedDeviceStore,
    serverPublicKey: ByteArray,
    tracker: TransferActivityTracker = NoOpTransferActivityTracker,
    deviceIdentityStore: DeviceIdentityStore? = null,
    discoveredDevicesStore: DiscoveredDevicesStore? = null,
    inboundEventBus: InboundEventBus? = null,
    cancelRegistry: InboundCancelRegistry? = null,
) {
    install(ContentNegotiation) { json() }
    routing {
        get(TetherRoutes.HEALTH) { call.respond(HttpStatusCode.OK, "Tether OK") }
        post(TetherRoutes.HELLO) {
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
            if (deviceIdentityStore != null && body.fingerprint == deviceIdentityStore.fingerprint()) {
                log.debug { "hello self-suppressed (own fingerprint) from ${body.alias}" }
                call.respond(HttpStatusCode.OK, emptyMap<String, String>())
                return@post
            }
            // remoteAddress is the numeric peer IP; remoteHost may reverse-resolve to a
            // hostname (e.g. "Mac") that the sender platform cannot resolve back to an IP.
            val remoteAddress = call.request.origin.remoteAddress
            val knownName = discoveredDevicesStore?.nameFor(body.fingerprint)
            val device = Device(
                name = knownName ?: body.alias,
                host = remoteAddress,
                port = body.port,
                fingerprint = body.fingerprint,
            )
            discoveredDevicesStore?.upsert(device)
            log.info { "hello from ${body.alias}@$remoteAddress:${body.port}" }
            call.respond(HttpStatusCode.OK, emptyMap<String, String>())
        }
        post(TetherRoutes.PAIR) {
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
        post(TetherRoutes.BATCH_BEGIN) {
            val body = try {
                call.receive<BatchBeginRequest>()
            } catch (_: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid_body"))
                return@post
            }
            if (body.totalFiles < 1) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid_batch"))
                return@post
            }
            val peer = call.resolvePeer(discoveredDevicesStore)
            if (peer != null && inboundEventBus != null) {
                inboundEventBus.emit(
                    InboundEvent.BatchStarted(
                        peer = peer,
                        batchId = body.batchId,
                        totalFiles = body.totalFiles,
                        totalBytes = body.totalBytes,
                    ),
                )
                log.info { "batch-begin from ${peer.id}: id=${body.batchId} files=${body.totalFiles}" }
            }
            call.respond(HttpStatusCode.OK, emptyMap<String, String>())
        }
        post(TetherRoutes.BATCH_CANCEL) {
            val body = try {
                call.receive<BatchCancelRequest>()
            } catch (_: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid_body"))
                return@post
            }
            val peer = call.resolvePeer(discoveredDevicesStore)
            if (peer != null && inboundEventBus != null) {
                inboundEventBus.emit(InboundEvent.BatchCancelled(peer = peer, batchId = body.batchId))
                log.info { "batch-cancel from ${peer.id}: id=${body.batchId}" }
            }
            call.respond(HttpStatusCode.OK, emptyMap<String, String>())
        }
        post(TetherRoutes.BATCH_END) {
            val body = try {
                call.receive<BatchEndRequest>()
            } catch (_: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid_body"))
                return@post
            }
            val peer = call.resolvePeer(discoveredDevicesStore)
            if (peer != null && inboundEventBus != null) {
                inboundEventBus.emit(InboundEvent.BatchEnd(peer = peer, batchId = body.batchId))
                log.info { "batch-end from ${peer.id}: id=${body.batchId}" }
            }
            call.respond(HttpStatusCode.OK, emptyMap<String, String>())
        }
        post(TetherRoutes.UPLOAD) {
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
            val peer = call.resolvePeer(discoveredDevicesStore)
            val contentLength = call.request.contentLength()
            var uploadComplete = false
            var cancelledByReceiver = false
            var handle: UploadHandle? = null
            try {
                val resolved = storage.resolveDestination(relativePath)
                handle = resolved
                val cancelSignal = if (peer != null && cancelRegistry != null) {
                    cancelRegistry.signalFor(peer)
                } else {
                    null
                }
                if (peer != null) inboundEventBus?.emit(InboundEvent.FileStarted(peer, relativePath))
                tracker.withActiveTransfer {
                    val body = call.receiveChannel()
                    var bytesWritten = 0L
                    coroutineScope {
                        val writeJob = launch {
                            bytesWritten = storage.writeBody(body, resolved)
                        }
                        if (cancelSignal != null) {
                            select {
                                writeJob.onJoin { }
                                cancelSignal.onAwait {
                                    cancelledByReceiver = true
                                    writeJob.cancel()
                                }
                            }
                        }
                        writeJob.join()
                    }
                    // Ktor closes the body channel silently when the client disconnects
                    // mid-stream. closedCause covers exceptional close; the Content-Length
                    // comparison covers clean close on incomplete bodies.
                    body.closedCause?.let { throw it }
                    if (cancelledByReceiver) throw CancelledByReceiverException()
                    val expected = contentLength
                    if (expected != null && bytesWritten < expected) {
                        error("FileServer: incomplete upload — got $bytesWritten of $expected bytes")
                    }
                    storage.commit(resolved)
                    uploadComplete = true
                    log.info { "received '$relativePath' — $bytesWritten bytes → ${resolved.destination}" }
                    if (peer != null) {
                        inboundEventBus?.emit(
                            InboundEvent.Progress(peer, relativePath, bytesWritten, contentLength),
                        )
                        inboundEventBus?.emit(
                            InboundEvent.FileCompleted(peer, relativePath, resolved.destination),
                        )
                    }
                    call.respond(HttpStatusCode.OK, mapOf("savedPath" to resolved.destination))
                }
            } catch (e: Exception) {
                log.error { "upload failed for '$relativePath' — ${e.message ?: "unknown error"}" }
                if (peer != null && !uploadComplete) {
                    val event = if (cancelledByReceiver) {
                        InboundEvent.CancelledByReceiver(peer)
                    } else {
                        InboundEvent.ConnectionLost(peer)
                    }
                    inboundEventBus?.emit(event)
                }
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

internal class CancelledByReceiverException : Exception("upload cancelled by receiver")

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
