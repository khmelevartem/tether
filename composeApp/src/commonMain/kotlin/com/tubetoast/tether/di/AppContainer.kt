package com.tubetoast.tether.di

import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.network.FileClient
import com.tubetoast.tether.network.FileServer
import com.tubetoast.tether.security.TrustedDeviceStore

abstract class AppContainer(
    config: AppConfig,
) {
    val deviceName: String = config.deviceName
    abstract val fileServer: FileServer
    abstract val mdnsDiscovery: MdnsDiscovery
    open val fileClient: FileClient = FileClient.default()
    abstract val trustedDeviceStore: TrustedDeviceStore
}
