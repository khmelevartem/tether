package com.tubetoast.tether.network

import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.runBlocking
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.error
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.withMessage
import ru.pocketbyte.kydra.log.wrapper.withTag
import java.io.File

private val log = KydraLog.withTag(default = "FileServer")

actual class FileServer(
    private val configuredPort: Int,
    private val downloadsDir: File = File(System.getProperty("user.home"), "Downloads/Tether"),
    private val trustedDeviceStore: TrustedDeviceStore,
    private val deviceKeyPair: DeviceKeyPair,
    private val tracker: TransferActivityTracker = DefaultTransferActivityTracker(),
    private val ownFingerprint: () -> String = { "" },
    private val discoveredDevicesStore: DiscoveredDevicesStore? = null,
) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    @Volatile private var _port: Int = -1
    actual val port: Int get() = _port

    actual fun start(): Int {
        check(server == null) { "FileServer is already running" }
        val storage = FileUploadStorage(
            root = downloadsDir.absolutePath,
            backend = JvmUploadStorageBackend(downloadsDir.absolutePath),
        )
        storage.ensureRoot()
        val srv = try {
            embeddedServer(CIO, port = configuredPort) {
                installFileServerRoutes(
                    storage,
                    trustedDeviceStore,
                    deviceKeyPair.publicKey,
                    tracker,
                    ownFingerprint,
                    discoveredDevicesStore,
                )
            }.start(wait = false)
        } catch (e: Exception) {
            log.error { e withMessage "FileServer start failed on port $configuredPort" }
            throw e
        }
        server = srv
        // resolvedConnectors() returns the actual OS-assigned port when port=0 was specified,
        // eliminating the TOCTOU race that would exist if we probed with ServerSocket(0) first.
        val resolvedPort = runBlocking { srv.engine.resolvedConnectors() }.first().port
        _port = resolvedPort
        log.info { "started on port $resolvedPort, downloads → ${downloadsDir.absolutePath}" }
        return resolvedPort
    }

    actual fun stop() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 1_000)
        server = null
        _port = -1
        log.info { "stopped" }
    }
}
