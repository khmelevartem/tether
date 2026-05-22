package com.tubetoast.tether.di

import com.tubetoast.tether.config.DeviceNamePersistence
import com.tubetoast.tether.config.DeviceNamePersistenceJvm
import com.tubetoast.tether.config.DeviceNameStore
import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.discovery.MdnsDiscovery

class DesktopAppContainer(
    config: DesktopAppConfig,
    namePersistence: DeviceNamePersistence = DeviceNamePersistenceJvm(),
) : JvmAppContainer(config) {
    override val nameStore: DeviceNameStore = DeviceNameStore(namePersistence)
    override val mdnsDiscovery: MdnsDiscovery = MdnsDiscovery(DiscoveredDevicesStore())
}
