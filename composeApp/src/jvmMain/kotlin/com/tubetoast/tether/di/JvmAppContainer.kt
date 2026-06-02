package com.tubetoast.tether.di

import com.tubetoast.tether.network.FileClient
import com.tubetoast.tether.network.FileServer
import com.tubetoast.tether.network.PairingConfirmationHandler
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
        FileClient.withPairing(
            trustedDeviceStore = trustedDeviceStore,
            ownKeyPair = config.deviceKeyPair,
            ownNameProvider = { nameStore.name.first() },
            pairingHandler = pairingConfirmationHandler,
            tracker = transferActivityTracker,
        )
    }
}
