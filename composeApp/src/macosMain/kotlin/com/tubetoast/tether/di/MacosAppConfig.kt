package com.tubetoast.tether.di

import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore

interface MacosAppConfig : AppleAppConfig

class DefaultMacosAppConfig(
    override val deviceName: String,
    override val trustedDeviceStore: TrustedDeviceStore = TrustedDeviceStore(),
    override val deviceKeyPair: DeviceKeyPair = DeviceKeyPair(),
) : MacosAppConfig
