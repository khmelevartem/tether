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
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.error
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.withMessage
import ru.pocketbyte.kydra.log.wrapper.withTag
import java.io.File

private val log = KydraLog.withTag(default = "Tether.FileServer")

actual class FileServer(
    private val port: Int,
    private val downloadsDir: File = File(System.getProperty("user.home"), "Downloads/Tether"),
    private val trustedDeviceStore: TrustedDeviceStore,
    private val deviceKeyPair: DeviceKeyPair,
    private val tracker: TransferActivityTracker = DefaultTransferActivityTracker(),
) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    actual fun start(): Int {
        check(server == null) { "FileServer is already running" }
        val storage = JvmUploadStorage(downloadsDir)
        storage.ensureRoot()
        val srv = try {
            embeddedServer(CIO, port = port) {
                installFileServerRoutes(storage, trustedDeviceStore, deviceKeyPair.publicKey, tracker)
            }.start(wait = false)
        } catch (e: Exception) {
            log.error { e withMessage "FileServer start failed on port $port" }
            throw e
        }
        server = srv
        // resolvedConnectors() returns the actual OS-assigned port when port=0 was specified,
        // eliminating the TOCTOU race that would exist if we probed with ServerSocket(0) first.
        val resolvedPort = runBlocking { srv.engine.resolvedConnectors() }.first().port
        log.info { "started on port $resolvedPort, downloads → ${downloadsDir.absolutePath}" }
        return resolvedPort
    }

    actual fun stop() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 1_000)
        server = null
        log.info { "stopped" }
    }
}

private class JvmUploadStorage(
    private val root: File,
) : UploadStorage {
    override fun ensureRoot() {
        root.mkdirs()
    }

    override fun resolveDestination(fileName: String): String =
        resolveDestinationFile(root, fileName).absolutePath

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
}

private fun resolveDestinationFile(dir: File, fileName: String): File {
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
