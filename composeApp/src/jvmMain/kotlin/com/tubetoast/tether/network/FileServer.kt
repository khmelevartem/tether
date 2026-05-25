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
import java.io.IOException

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
    private val rootReal by lazy { root.toPath().toRealPath() }

    override fun ensureRoot() {
        root.mkdirs()
    }

    override fun resolveDestination(relativePath: String): String {
        val dest = resolveDestinationFile(root, relativePath)
        val created = mkdirsTracked(dest.parentFile)
        try {
            val destReal = if (dest.exists()) {
                dest.toPath().toRealPath()
            } else {
                // resolveDestinationFile always returns a File under root, so parentFile is non-null.
                dest.parentFile!!
                    .toPath()
                    .toRealPath()
                    .resolve(dest.name)
            }
            if (!destReal.startsWith(rootReal)) {
                throw IOException("destination escapes downloads root: $dest")
            }
        } catch (e: Throwable) {
            created.asReversed().forEach { it.delete() }
            throw e
        }
        return dest.absolutePath
    }

    override suspend fun writeBody(body: ByteReadChannel, destination: String): Long =
        body.toInputStream().use { input ->
            File(destination).outputStream().use { output ->
                input.copyTo(output, bufferSize = UPLOAD_BUFFER_SIZE)
            }
        }

    override fun abort(destination: String) {
        val dest = File(destination)
        dest.delete()
        var dir = dest.parentFile
        while (dir != null && dir != root && dir.exists() && dir.list()?.isEmpty() == true) {
            if (!dir.delete()) break
            dir = dir.parentFile
        }
    }
}

private fun mkdirsTracked(dir: File?): List<File> {
    if (dir == null || dir.exists()) return emptyList()
    val toCreate = mutableListOf<File>()
    var cur: File? = dir
    while (cur != null && !cur.exists()) {
        toCreate += cur
        cur = cur.parentFile
    }
    dir.mkdirs()
    return toCreate.asReversed()
}

private fun resolveDestinationFile(dir: File, relativePath: String): File {
    val initial = File(dir, relativePath)
    val leaf = dedupFilename(initial.name) { candidate -> File(initial.parentFile, candidate).exists() }
    return File(initial.parentFile, leaf)
}
