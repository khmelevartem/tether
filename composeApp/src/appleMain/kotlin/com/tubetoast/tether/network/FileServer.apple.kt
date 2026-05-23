@file:OptIn(ExperimentalForeignApi::class)

package com.tubetoast.tether.network

import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.utils.io.ByteReadChannel
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fopen
import platform.posix.fwrite
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.error
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.withMessage
import ru.pocketbyte.kydra.log.wrapper.withTag

private val log = KydraLog.withTag(default = "Tether.FileServer")

actual class FileServer(
    private val port: Int,
    downloadsDir: String? = null,
    private val trustedDeviceStore: TrustedDeviceStore,
    private val deviceKeyPair: DeviceKeyPair,
    private val tracker: TransferActivityTracker = DefaultTransferActivityTracker(),
) {
    private val downloadsDir: String = downloadsDir ?: defaultDownloadsDir()
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    actual fun start(): Int {
        check(server == null) { "FileServer is already running" }
        val storage = AppleUploadStorage(downloadsDir)
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
        val resolvedPort = runBlocking { srv.engine.resolvedConnectors() }.first().port
        log.info { "started on port $resolvedPort, downloads → $downloadsDir" }
        return resolvedPort
    }

    actual fun stop() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 1_000)
        server = null
        log.info { "stopped" }
    }
}

private class AppleUploadStorage(
    private val root: String,
) : UploadStorage {
    override fun ensureRoot() {
        val fm = NSFileManager.defaultManager
        if (!fm.fileExistsAtPath(root)) {
            fm.createDirectoryAtPath(
                path = root,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        }
    }

    override fun resolveDestination(fileName: String): String {
        val fm = NSFileManager.defaultManager
        val firstTry = "$root/$fileName"
        if (!fm.fileExistsAtPath(firstTry)) return firstTry
        val ext = fileName.substringAfterLast('.', "")
        val base = if (ext.isEmpty()) fileName else fileName.removeSuffix(".$ext")
        var i = 1
        while (true) {
            val candidate = if (ext.isEmpty()) "$root/${base}_$i" else "$root/${base}_$i.$ext"
            if (!fm.fileExistsAtPath(candidate)) return candidate
            i++
        }
    }

    override suspend fun writeBody(body: ByteReadChannel, destination: String): Long {
        val file = fopen(destination, "wb")
            ?: error("FileServer: could not open '$destination' for writing")
        var total = 0L
        try {
            streamUploadBody(body) { buffer, n ->
                buffer.usePinned { pinned ->
                    // POSIX: short fwrite return signals an I/O error (disk full,
                    // quota, etc.). Without the check the upload would silently
                    // truncate while the route responded 200 OK.
                    val written = fwrite(pinned.addressOf(0), 1u, n.toULong(), file).toLong()
                    if (written < n.toLong()) {
                        error("FileServer: short write to '$destination' — wrote $written of $n bytes")
                    }
                }
                total += n.toLong()
            }
            // fflush surfaces deferred stdio errors before the route responds.
            // fclose still runs in finally; its error is ignored because fflush
            // already validated the data reached the OS.
            if (fflush(file) != 0) {
                error("FileServer: fflush failed for '$destination'")
            }
        } finally {
            fclose(file)
        }
        return total
    }

    override fun deleteIfExists(destination: String) {
        val fm = NSFileManager.defaultManager
        if (fm.fileExistsAtPath(destination)) {
            fm.removeItemAtPath(destination, error = null)
        }
    }
}

private fun defaultDownloadsDir(): String {
    val docs = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory,
        NSUserDomainMask,
        true,
    ).firstOrNull() as? String
        ?: error("FileServer: NSDocumentDirectory unavailable")
    return "$docs/Tether"
}
