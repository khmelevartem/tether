package com.tubetoast.tether.network

import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.identity.DeviceIdentityStore
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
    private val configuredPort: Int,
    downloadsDir: String? = null,
    private val trustedDeviceStore: TrustedDeviceStore,
    private val deviceKeyPair: DeviceKeyPair,
    private val tracker: TransferActivityTracker = DefaultTransferActivityTracker(),
    private val deviceIdentityStore: DeviceIdentityStore? = null,
    private val discoveredDevicesStore: DiscoveredDevicesStore? = null,
    private val pairingConfirmationHandler: PairingConfirmationHandler? = null,
    private val pairingTimeoutMillis: Long = 30_000L,
) {
    private val downloadsDir: String = downloadsDir ?: defaultDownloadsDir()
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    private var _port: Int = -1
    actual val port: Int get() = _port

    actual fun start(): Int {
        check(server == null) { "FileServer is already running" }
        val storage = FileUploadStorage(
            root = this.downloadsDir,
            backend = AppleUploadStorageBackend(this.downloadsDir),
        )
        storage.ensureRoot()
        val srv = try {
            embeddedServer(CIO, port = configuredPort) {
                installFileServerRoutes(
                    storage,
                    trustedDeviceStore,
                    deviceKeyPair.publicKey,
                    tracker,
                    deviceIdentityStore,
                    discoveredDevicesStore,
                    pairingConfirmationHandler,
                    pairingTimeoutMillis,
                )
            }.start(wait = false)
        } catch (e: Exception) {
            log.error { e withMessage "FileServer start failed on port $configuredPort" }
            throw e
        }
        server = srv
        val resolvedPort = runBlocking { srv.engine.resolvedConnectors() }.first().port
        _port = resolvedPort
        log.info { "started on port $resolvedPort, downloads → ${this.downloadsDir}" }
        return resolvedPort
    }

    actual fun stop() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 1_000)
        server = null
        _port = -1
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
