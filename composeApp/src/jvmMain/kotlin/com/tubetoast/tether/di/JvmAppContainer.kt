package com.tubetoast.tether.di

import com.tubetoast.tether.network.FileServer
import kotlinx.coroutines.runBlocking
import java.io.File

abstract class JvmAppContainer(
    private val config: JvmAppConfig,
) : AppContainer() {
    val downloadsDir: File = config.downloadsDir
    override val fileServer: FileServer by lazy {
        FileServer(
            configuredPort = config.port,
            downloadsDir = downloadsDir,
            trustedDeviceStore = trustedDeviceStore,
            deviceKeyPair = config.deviceKeyPair,
            tracker = transferActivityTracker,
            ownFingerprint = { runBlocking { deviceIdentityStore.getOrCreate() } },
            discoveredDevicesStore = discoveredDevicesStore,
        )
    }
}
