package com.tubetoast.tether.di

import com.tubetoast.tether.security.DeviceKeyPair

interface IosAppConfig : AppleAppConfig

class DefaultIosAppConfig(
    override val deviceKeyPair: DeviceKeyPair = DeviceKeyPair(),
) : IosAppConfig
