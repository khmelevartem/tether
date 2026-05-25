@file:OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.tubetoast.tether.network

import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.utils.io.ByteReadChannel
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.posix.PATH_MAX
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fopen
import platform.posix.fwrite
import platform.posix.realpath
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.error
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.withMessage
import ru.pocketbyte.kydra.log.wrapper.withTag

internal class IOException(
    message: String,
) : Exception(message)

private val log = KydraLog.withTag(default = "FileServer")

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
    private val rootReal: String by lazy {
        realpathOf(root) ?: throw IOException("FileServer: realpath failed for downloads root: $root")
    }

    override fun ensureRoot() {
        mkdirsChecked(root)
    }

    override fun resolveDestination(relativePath: String): String {
        val leafName = relativePath.substringAfterLast('/')
        val parentDir = if (relativePath.contains('/')) "$root/${relativePath.substringBeforeLast('/')}" else root
        var created: List<String> = emptyList()
        try {
            created = mkdirsTracked(parentDir)
            val resolvedParent = realpathOf(parentDir)
                ?: throw IOException("destination escapes downloads root: realpath failed for $parentDir")
            if (!resolvedParent.startsWith(rootReal + "/") && resolvedParent != rootReal) {
                throw IOException("destination escapes downloads root: $resolvedParent")
            }

            val leaf = dedupFilename(leafName) { candidate ->
                NSFileManager.defaultManager.fileExistsAtPath("$resolvedParent/$candidate")
            }
            return "$resolvedParent/$leaf"
        } catch (e: Throwable) {
            created.asReversed().forEach { deleteIfEmpty(it) }
            throw e
        }
    }

    override suspend fun writeBody(body: ByteReadChannel, destination: String): Long {
        val file = fopen(destination, "wb")
            ?: throw IOException("FileServer: could not open '$destination' for writing")
        var total = 0L
        try {
            streamUploadBody(body) { buffer, n ->
                buffer.usePinned { pinned ->
                    // POSIX: short fwrite return signals an I/O error (disk full,
                    // quota, etc.). Without the check the upload would silently
                    // truncate while the route responded 200 OK.
                    val written = fwrite(pinned.addressOf(0), 1u, n.toULong(), file).toLong()
                    if (written < n.toLong()) {
                        throw IOException("FileServer: short write to '$destination' — wrote $written of $n bytes")
                    }
                }
                total += n.toLong()
            }
            // fflush surfaces deferred stdio errors before the route responds.
            // fclose still runs in finally; its error is ignored because fflush
            // already validated the data reached the OS.
            if (fflush(file) != 0) {
                throw IOException("FileServer: fflush failed for '$destination'")
            }
        } finally {
            fclose(file)
        }
        return total
    }

    override fun abort(destination: String) {
        NSFileManager.defaultManager.removeItemAtPath(destination, error = null)
        var dir = destination.substringBeforeLast('/', missingDelimiterValue = "")
        while (dir.isNotEmpty() && dir != rootReal) {
            if (!deleteIfEmpty(dir)) break
            dir = dir.substringBeforeLast('/', missingDelimiterValue = "")
        }
    }

    private fun deleteIfEmpty(path: String): Boolean {
        val fm = NSFileManager.defaultManager
        val contents = fm.contentsOfDirectoryAtPath(path, null) ?: return false
        if (contents.isNotEmpty()) return false
        return fm.removeItemAtPath(path, error = null)
    }
}

private fun mkdirsChecked(path: String) {
    val fm = NSFileManager.defaultManager
    if (fm.fileExistsAtPath(path)) return
    memScoped {
        val errorPtr = alloc<ObjCObjectVar<NSError?>>()
        val ok = fm.createDirectoryAtPath(
            path = path,
            withIntermediateDirectories = true,
            attributes = null,
            error = errorPtr.ptr,
        )
        if (!ok) {
            val msg = errorPtr.value?.localizedDescription ?: "unknown error"
            throw IOException("FileServer: createDirectory failed for $path: $msg")
        }
    }
}

private fun mkdirsTracked(dir: String): List<String> {
    val fm = NSFileManager.defaultManager
    if (fm.fileExistsAtPath(dir)) return emptyList()
    val toCreate = mutableListOf<String>()
    var cur: String? = dir
    while (cur != null && cur.isNotEmpty() && cur != "/" && !fm.fileExistsAtPath(cur)) {
        toCreate += cur
        val parent = cur.substringBeforeLast('/', missingDelimiterValue = "")
        cur = if (parent.isEmpty() || parent == cur) null else parent
    }
    mkdirsChecked(dir)
    return toCreate.asReversed()
}

private fun realpathOf(path: String): String? = memScoped {
    val buf = allocArray<ByteVar>(PATH_MAX)
    val result = realpath(path, buf) ?: return null
    result.toKString()
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
