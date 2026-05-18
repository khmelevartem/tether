package com.tubetoast.tether.di

import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore

interface JvmAppConfig : AppConfig {
    val port: Int
    val trustedDeviceStore: TrustedDeviceStore
    val deviceKeyPair: DeviceKeyPair
}
