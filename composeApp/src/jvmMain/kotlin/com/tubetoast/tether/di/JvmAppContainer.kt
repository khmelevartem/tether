package com.tubetoast.tether.di

import com.tubetoast.tether.network.FileServer
import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore
import java.io.File

abstract class JvmAppContainer(
    config: JvmAppConfig,
    trustedDeviceStore: TrustedDeviceStore,
) : AppContainer(config) {
    val downloadsDir: File = config.downloadsDir
    private val deviceKeyPair: DeviceKeyPair = DeviceKeyPair()
    override val trustedDeviceStore: TrustedDeviceStore = trustedDeviceStore
    override val fileServer: FileServer = FileServer(config.port, downloadsDir, trustedDeviceStore, deviceKeyPair)
}
