package com.tubetoast.tether.di

import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.network.FileServer
import com.tubetoast.tether.security.TrustedDeviceStore

open class AppleAppContainer(
    config: AppleAppConfig,
) : AppContainer(config) {
    override val trustedDeviceStore: TrustedDeviceStore = config.trustedDeviceStore
    override val fileServer: FileServer = FileServer(
        port = 0,
        trustedDeviceStore = config.trustedDeviceStore,
        deviceKeyPair = config.deviceKeyPair,
    )
    override val mdnsDiscovery: MdnsDiscovery = MdnsDiscovery()
}
