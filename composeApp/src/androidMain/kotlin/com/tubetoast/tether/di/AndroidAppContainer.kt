package com.tubetoast.tether.di

import com.tubetoast.tether.config.DeviceNamePersistenceAndroid
import com.tubetoast.tether.config.DeviceNameStore
import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.network.AndroidTransferLockHolder
import com.tubetoast.tether.network.DefaultTransferActivityTracker
import com.tubetoast.tether.network.TransferActivityTracker

class AndroidAppContainer(
    config: AndroidAppConfig,
) : JvmAppContainer(config) {
    val application = config.application
    private val lockHolder = AndroidTransferLockHolder(application)
    override val transferActivityTracker: TransferActivityTracker = DefaultTransferActivityTracker(
        onFirstEnter = lockHolder::acquire,
        onLastExit = lockHolder::release,
    )
    override val nameStore: DeviceNameStore = DeviceNameStore(DeviceNamePersistenceAndroid(application))
    override val mdnsDiscovery: MdnsDiscovery = MdnsDiscovery(application, DiscoveredDevicesStore())
}
