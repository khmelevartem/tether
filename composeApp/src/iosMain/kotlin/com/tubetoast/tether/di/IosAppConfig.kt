package com.tubetoast.tether.di

import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.KeychainStore

interface IosAppConfig : AppleAppConfig

class DefaultIosAppConfig(
    override val deviceKeyPair: DeviceKeyPair = DeviceKeyPair(
        keychain = KeychainStore.real(KeychainStore.DEFAULT_DEVICE_KEY_TAG),
    ),
) : IosAppConfig
