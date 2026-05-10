package com.tubetoast.tether.di

import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.network.FileServer

open class AppleAppContainer(
    config: AppleAppConfig,
) : AppContainer(config) {
    override val fileServer: FileServer = FileServer(port = 0)
    override val mdnsDiscovery: MdnsDiscovery = MdnsDiscovery()
}
