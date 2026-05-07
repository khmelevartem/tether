package com.tubetoast.tether.di

import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.network.FileServer

abstract class AppContainer(
    config: AppConfig,
) {
    val deviceName: String = config.deviceName
    abstract val fileServer: FileServer
    abstract val mdnsDiscovery: MdnsDiscovery
}
