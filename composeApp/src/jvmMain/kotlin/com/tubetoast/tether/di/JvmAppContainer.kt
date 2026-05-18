package com.tubetoast.tether.di

import com.tubetoast.tether.network.FileServer
import com.tubetoast.tether.security.TrustedDeviceStore

abstract class JvmAppContainer(
    private val config: JvmAppConfig,
) : AppContainer(config) {
    override val trustedDeviceStore: TrustedDeviceStore get() = config.trustedDeviceStore
    abstract override val fileServer: FileServer
}
