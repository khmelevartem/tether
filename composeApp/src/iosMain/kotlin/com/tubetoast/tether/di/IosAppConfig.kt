package com.tubetoast.tether.di

import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore

interface IosAppConfig : AppleAppConfig

class DefaultIosAppConfig(
    override val deviceName: String,
    override val trustedDeviceStore: TrustedDeviceStore = TrustedDeviceStore(),
    override val deviceKeyPair: DeviceKeyPair = DeviceKeyPair(),
) : IosAppConfig
