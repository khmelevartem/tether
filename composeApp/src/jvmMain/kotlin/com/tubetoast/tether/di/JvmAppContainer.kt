package com.tubetoast.tether.di

import com.tubetoast.tether.network.FileServer
import com.tubetoast.tether.security.TrustedDeviceStore
import java.io.File

abstract class JvmAppContainer(
    private val config: JvmAppConfig,
) : AppContainer(config) {
    val downloadsDir: File = config.downloadsDir
    override val trustedDeviceStore: TrustedDeviceStore get() = config.trustedDeviceStore
    open override val fileServer: FileServer by lazy {
        FileServer(
            port = config.port,
            downloadsDir = downloadsDir,
            trustedDeviceStore = config.trustedDeviceStore,
            deviceKeyPair = config.deviceKeyPair,
            tracker = transferActivityTracker,
        )
    }
}
