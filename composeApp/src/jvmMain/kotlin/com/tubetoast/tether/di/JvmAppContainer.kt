package com.tubetoast.tether.di

import com.tubetoast.tether.network.FileClient
import com.tubetoast.tether.network.FileServer
import com.tubetoast.tether.network.PairingConfirmationHandler
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import java.io.File

abstract class JvmAppContainer(
    private val config: JvmAppConfig,
) : AppContainer() {
    val downloadsDir: File = config.downloadsDir

    open val pairingConfirmationHandler: PairingConfirmationHandler? = null

    override val fileServer: FileServer by lazy {
        FileServer(
            configuredPort = config.port,
            downloadsDir = downloadsDir,
            trustedDeviceStore = trustedDeviceStore,
            deviceKeyPair = config.deviceKeyPair,
            tracker = transferActivityTracker,
            deviceIdentityStore = deviceIdentityStore,
            discoveredDevicesStore = discoveredDevicesStore,
            pairingConfirmationHandler = pairingConfirmationHandler,
        )
    }

    override val fileClient: FileClient by lazy {
        FileClient(
            client = HttpClient(CIO) {
                install(ContentNegotiation) { json() }
                install(HttpTimeout) {
                    requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                    socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                }
            },
            tracker = transferActivityTracker,
            trustedDeviceStore = trustedDeviceStore,
            ownKeyPair = config.deviceKeyPair,
            ownNameProvider = { nameStore.name.first() },
            pairingHandler = pairingConfirmationHandler,
        )
    }
}
