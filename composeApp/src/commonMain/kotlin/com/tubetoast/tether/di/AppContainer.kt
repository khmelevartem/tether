package com.tubetoast.tether.di

import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.network.DefaultTransferActivityTracker
import com.tubetoast.tether.network.FileClient
import com.tubetoast.tether.network.FileServer
import com.tubetoast.tether.network.TransferActivityTracker
import com.tubetoast.tether.security.TrustedDeviceStore

abstract class AppContainer(
    config: AppConfig,
) {
    val deviceName: String = config.deviceName
    abstract val fileServer: FileServer
    abstract val mdnsDiscovery: MdnsDiscovery
    open val transferActivityTracker: TransferActivityTracker = DefaultTransferActivityTracker()
    open val fileClient: FileClient by lazy { FileClient.default(transferActivityTracker) }
    abstract val trustedDeviceStore: TrustedDeviceStore
}
