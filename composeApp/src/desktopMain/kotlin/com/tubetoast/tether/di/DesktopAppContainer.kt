package com.tubetoast.tether.di

import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore

class DesktopAppContainer(
    config: DesktopAppConfig,
) : JvmAppContainer(config, TrustedDeviceStore(), DeviceKeyPair()) {
    override val mdnsDiscovery: MdnsDiscovery = MdnsDiscovery()
}
