package com.tubetoast.tether.di

import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore
import java.io.File

interface DesktopAppConfig : JvmAppConfig

class DefaultDesktopAppConfig(
    override val port: Int,
    override val downloadsDir: File = File(System.getProperty("user.home"), "Downloads/Tether"),
    override val trustedDeviceStore: TrustedDeviceStore = TrustedDeviceStore(),
    override val deviceKeyPair: DeviceKeyPair = DeviceKeyPair(),
) : DesktopAppConfig
