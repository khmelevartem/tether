package com.tubetoast.tether.di

import com.tubetoast.tether.config.DeviceNameStore
import com.tubetoast.tether.discovery.DeviceNameRepublisher
import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.network.DefaultTransferActivityTracker
import com.tubetoast.tether.network.FileClient
import com.tubetoast.tether.network.FileServer
import com.tubetoast.tether.network.TransferActivityTracker
import com.tubetoast.tether.security.TrustedDeviceStore

abstract class AppContainer {
    abstract val nameStore: DeviceNameStore
    abstract val fileServer: FileServer
    abstract val mdnsDiscovery: MdnsDiscovery
    open val transferActivityTracker: TransferActivityTracker = DefaultTransferActivityTracker()
    open val fileClient: FileClient by lazy { FileClient.default(transferActivityTracker) }
    abstract val trustedDeviceStore: TrustedDeviceStore
    open val nameRepublisher: DeviceNameRepublisher by lazy {
        DeviceNameRepublisher(nameStore, mdnsDiscovery)
    }
}
