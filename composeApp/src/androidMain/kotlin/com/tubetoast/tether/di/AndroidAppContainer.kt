package com.tubetoast.tether.di

import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.network.AndroidMediaStoreUploadStorage
import com.tubetoast.tether.network.AndroidTransferLockHolder
import com.tubetoast.tether.network.DefaultTransferActivityTracker
import com.tubetoast.tether.network.FileServer
import com.tubetoast.tether.network.TransferActivityTracker
import com.tubetoast.tether.transfer.FilePicker

class AndroidAppContainer(
    private val config: AndroidAppConfig,
) : JvmAppContainer(config) {
    internal val androidFilePickerResource: ActivityResource<FilePicker> = ActivityResource()
    val application = config.application
    private val lockHolder = AndroidTransferLockHolder(application)
    override val transferActivityTracker: TransferActivityTracker = DefaultTransferActivityTracker(
        onFirstEnter = lockHolder::acquire,
        onLastExit = lockHolder::release,
    )
    override val mdnsDiscovery: MdnsDiscovery = MdnsDiscovery(application, DiscoveredDevicesStore())
    override val fileServer: FileServer by lazy {
        FileServer(
            port = config.port,
            trustedDeviceStore = config.trustedDeviceStore,
            deviceKeyPair = config.deviceKeyPair,
            storage = AndroidMediaStoreUploadStorage(application),
            tracker = transferActivityTracker,
        )
    }
}
