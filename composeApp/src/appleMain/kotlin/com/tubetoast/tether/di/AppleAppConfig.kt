package com.tubetoast.tether.di

import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore

interface AppleAppConfig : AppConfig {
    val trustedDeviceStore: TrustedDeviceStore
    val deviceKeyPair: DeviceKeyPair
}
