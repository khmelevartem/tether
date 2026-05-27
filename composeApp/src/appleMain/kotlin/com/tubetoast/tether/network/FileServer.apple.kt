package com.tubetoast.tether.network

import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.error
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.withMessage
import ru.pocketbyte.kydra.log.wrapper.withTag

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
        val storage = FileUploadStorage(
            root = this.downloadsDir,
            backend = AppleUploadStorageBackend(this.downloadsDir),
        )
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
        log.info { "started on port $resolvedPort, downloads → ${this.downloadsDir}" }
        return resolvedPort
    }

    actual fun stop() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 1_000)
        server = null
        log.info { "stopped" }
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
