package com.tubetoast.tether.di

import com.tubetoast.tether.network.DEFAULT_DOWNLOADS_SUBDIR
import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore
import java.io.File

interface DesktopAppConfig : JvmAppConfig

class DefaultDesktopAppConfig(
    override val deviceName: String,
    override val port: Int,
    override val downloadsDir: File = File(System.getProperty("user.home"), DEFAULT_DOWNLOADS_SUBDIR),
    override val trustedDeviceStore: TrustedDeviceStore = TrustedDeviceStore(),
    override val deviceKeyPair: DeviceKeyPair = DeviceKeyPair(),
) : DesktopAppConfig
