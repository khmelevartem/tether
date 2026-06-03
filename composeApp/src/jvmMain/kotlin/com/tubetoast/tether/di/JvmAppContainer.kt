package com.tubetoast.tether.di

import com.tubetoast.tether.network.FileServer
import com.tubetoast.tether.network.PairingConfirmationHandler
import com.tubetoast.tether.security.DeviceKeyPair
import java.io.File

abstract class JvmAppContainer(
    private val config: JvmAppConfig,
) : AppContainer() {
    val downloadsDir: File = config.downloadsDir
    protected val deviceKeyPair: DeviceKeyPair = config.deviceKeyPair

    // Server-side PIN confirmation; null = auto-accept (platforms without a confirmation UI yet).
    open val pairingConfirmationHandler: PairingConfirmationHandler? = null

    override val fileServer: FileServer by lazy {
        FileServer(
            configuredPort = config.port,
            downloadsDir = downloadsDir,
            trustedDeviceStore = trustedDeviceStore,
            deviceKeyPair = deviceKeyPair,
            tracker = transferActivityTracker,
            deviceIdentityStore = deviceIdentityStore,
            discoveredDevicesStore = discoveredDevicesStore,
            pairingConfirmationHandler = pairingConfirmationHandler,
        )
    }
}
