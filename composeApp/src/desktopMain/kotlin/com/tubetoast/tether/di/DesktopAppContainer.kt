package com.tubetoast.tether.di

import com.tubetoast.tether.config.DeviceNamePersistence
import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.discovery.MdnsDiscovery

class DesktopAppContainer(
    config: DesktopAppConfig,
) : JvmAppContainer(config) {
    override val namePersistence: DeviceNamePersistence = config.namePersistence
    override val mdnsDiscovery: MdnsDiscovery = MdnsDiscovery(DiscoveredDevicesStore())
}
