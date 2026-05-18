package com.tubetoast.tether.network

import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.runBlocking
import java.io.File

actual class FileServer internal constructor(
    private val port: Int,
    private val trustedDeviceStore: TrustedDeviceStore,
    private val deviceKeyPair: DeviceKeyPair,
    private val storage: UploadStorage,
    private val tracker: TransferActivityTracker = DefaultTransferActivityTracker(),
) {
    constructor(
        port: Int,
        downloadsDir: File = File(System.getProperty("user.home"), DEFAULT_DOWNLOADS_SUBDIR),
        trustedDeviceStore: TrustedDeviceStore,
        deviceKeyPair: DeviceKeyPair,
        tracker: TransferActivityTracker = DefaultTransferActivityTracker(),
    ) : this(port, trustedDeviceStore, deviceKeyPair, JvmUploadStorage(downloadsDir), tracker)

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    actual fun start(): Int {
        check(server == null) { "FileServer is already running" }
        storage.ensureRoot()
        val srv = embeddedServer(CIO, port = port) {
            installFileServerRoutes(storage, trustedDeviceStore, deviceKeyPair.publicKey, tracker)
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

private class JvmUploadStorage(
    private val root: File,
) : UploadStorage {
    override fun ensureRoot() {
        root.mkdirs()
    }

    override fun resolveDestination(fileName: String): String {
        val dest = resolveDestinationFile(root, fileName)
        val canonical = dest.canonicalPath
        val rootCanonical = root.canonicalPath
        check(canonical.startsWith(rootCanonical + File.separator) || canonical == rootCanonical) {
            "FileServer: path escape attempt detected: $canonical"
        }
        dest.parentFile?.mkdirs()
        return canonical
    }

    override suspend fun writeBody(body: ByteReadChannel, destination: String): Long =
        body.toInputStream().use { input ->
            File(destination).outputStream().use { output ->
                input.copyTo(output, bufferSize = UPLOAD_BUFFER_SIZE)
            }
        }

    override fun deleteIfExists(destination: String) {
        try {
            File(destination).delete()
        } catch (_: Exception) {
        }
    }

    override fun logInfo(message: String) {
        println("[FileServer] $message")
    }

    override fun logError(message: String) {
        System.err.println("[FileServer] ERROR: $message")
    }
}
