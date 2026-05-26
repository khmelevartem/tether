package com.tubetoast.tether.di

import com.tubetoast.tether.security.DeviceKeyPair

interface AppleAppConfig : AppConfig {
    val deviceKeyPair: DeviceKeyPair
}
