package com.tubetoast.tether.di

import com.tubetoast.tether.network.FileServer
import com.tubetoast.tether.network.FileUploadStorage
import com.tubetoast.tether.network.JvmUploadStorageBackend
import com.tubetoast.tether.network.UploadStorage
import com.tubetoast.tether.security.DeviceKeyPair
import java.io.File

abstract class JvmAppContainer(
    private val config: JvmAppConfig,
) : AppContainer() {
    val downloadsDir: File = config.downloadsDir
    override val deviceKeyPair: DeviceKeyPair = config.deviceKeyPair
    internal open val uploadStorage: UploadStorage by lazy {
        FileUploadStorage(
            root = downloadsDir.absolutePath,
            backend = JvmUploadStorageBackend(downloadsDir.absolutePath),
        )
    }
    override val fileServer: FileServer by lazy {
        FileServer(
            configuredPort = config.port,
            uploadStorage = uploadStorage,
            trustedDeviceStore = trustedDeviceStore,
            deviceKeyPair = deviceKeyPair,
            inboundEventBus = inboundEventBus,
            cancelRegistry = inboundCancelRegistry,
            tracker = transferActivityTracker,
            deviceIdentityStore = deviceIdentityStore,
            discoveredDevicesStore = discoveredDevicesStore,
        )
    }
}
