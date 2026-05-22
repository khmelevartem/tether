package com.tubetoast.tether.di

import com.tubetoast.tether.config.DeviceNamePersistence
import com.tubetoast.tether.config.DeviceNamePersistenceJvm
import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore
import java.io.File

interface DesktopAppConfig : JvmAppConfig {
    val namePersistence: DeviceNamePersistence
}

class DefaultDesktopAppConfig(
    override val port: Int,
    override val downloadsDir: File = File(System.getProperty("user.home"), "Downloads/Tether"),
    override val trustedDeviceStore: TrustedDeviceStore = TrustedDeviceStore(),
    override val deviceKeyPair: DeviceKeyPair = DeviceKeyPair(),
    override val namePersistence: DeviceNamePersistence = DeviceNamePersistenceJvm(),
) : DesktopAppConfig
