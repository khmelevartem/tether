package com.tubetoast.tether.di

import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.network.FileServer

class DesktopAppContainer(
    private val config: DesktopAppConfig,
) : JvmAppContainer(config) {
    override val mdnsDiscovery: MdnsDiscovery = MdnsDiscovery(DiscoveredDevicesStore())
    override val fileServer: FileServer by lazy {
        FileServer(
            port = config.port,
            downloadsDir = config.downloadsDir,
            trustedDeviceStore = config.trustedDeviceStore,
            deviceKeyPair = config.deviceKeyPair,
            tracker = transferActivityTracker,
        )
    }
}
