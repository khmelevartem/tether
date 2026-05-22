package com.tubetoast.tether.di

import com.tubetoast.tether.config.DeviceNamePersistence
import com.tubetoast.tether.config.DeviceNamePersistenceApple
import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.network.FileServer
import com.tubetoast.tether.security.TrustedDeviceStore

open class AppleAppContainer(
    private val config: AppleAppConfig,
) : AppContainer() {
    override val namePersistence: DeviceNamePersistence = DeviceNamePersistenceApple()
    override val trustedDeviceStore: TrustedDeviceStore get() = config.trustedDeviceStore
    override val fileServer: FileServer by lazy {
        FileServer(
            port = 0,
            trustedDeviceStore = config.trustedDeviceStore,
            deviceKeyPair = config.deviceKeyPair,
            tracker = transferActivityTracker,
        )
    }
    override val mdnsDiscovery: MdnsDiscovery = MdnsDiscovery(DiscoveredDevicesStore())
}
