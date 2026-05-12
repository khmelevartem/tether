package com.tubetoast.tether.di

import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore
import java.io.File

interface JvmAppConfig : AppConfig {
    val port: Int
    val downloadsDir: File
    val trustedDeviceStore: TrustedDeviceStore
    val deviceKeyPair: DeviceKeyPair
}
