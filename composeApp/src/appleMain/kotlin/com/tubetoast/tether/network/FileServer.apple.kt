package com.tubetoast.tether.network

import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.identity.DeviceIdentityStore
import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore
import com.tubetoast.tether.transfer.NoOpTransferActivityTracker
import com.tubetoast.tether.transfer.TransferActivityTracker
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

private val log = KydraLog.withTag(default = "FileServer")

actual class FileServer internal constructor(
    private val configuredPort: Int,
    private val uploadStorage: UploadStorage,
    private val trustedDeviceStore: TrustedDeviceStore,
    private val deviceKeyPair: DeviceKeyPair,
    private val tracker: TransferActivityTracker = NoOpTransferActivityTracker,
    private val deviceIdentityStore: DeviceIdentityStore? = null,
    private val discoveredDevicesStore: DiscoveredDevicesStore? = null,
) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    private var _port: Int = -1
    actual val port: Int get() = _port

    actual fun start(): Int {
        check(server == null) { "FileServer is already running" }
        uploadStorage.ensureRoot()
        val srv = try {
            embeddedServer(CIO, port = configuredPort) {
                installFileServerRoutes(
                    uploadStorage,
                    trustedDeviceStore,
                    deviceKeyPair.publicKey,
                    tracker,
                    deviceIdentityStore,
                    discoveredDevicesStore,
                )
            }.start(wait = false)
        } catch (e: Exception) {
            log.error { e withMessage "FileServer start failed on port $configuredPort" }
            throw e
        }
        server = srv
        val resolvedPort = runBlocking { srv.engine.resolvedConnectors() }.first().port
        _port = resolvedPort
        log.info { "started on port $resolvedPort" }
        return resolvedPort
    }

    actual fun stop() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 1_000)
        server = null
        _port = -1
        log.info { "stopped" }
    }
}
