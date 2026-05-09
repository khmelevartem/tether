package com.tubetoast.tether.di

import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.network.FileServer

open class AppleAppContainer(
    config: AppleAppConfig,
) : AppContainer(config) {
    // Port 0 = OS-assigned ephemeral port. Apple targets have no CLI/Settings
    // surface to choose a port today; if one appears (macOS CLI, iOS Settings)
    // it would extend AppleAppConfig, mirroring JvmAppConfig.port.
    override val fileServer: FileServer = FileServer(port = 0)
    override val mdnsDiscovery: MdnsDiscovery = MdnsDiscovery()
}
