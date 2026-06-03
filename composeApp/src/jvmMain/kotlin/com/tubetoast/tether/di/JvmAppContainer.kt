package com.tubetoast.tether.di

import com.tubetoast.tether.network.FileServer
import com.tubetoast.tether.transfer.FilePicker
import com.tubetoast.tether.transfer.FileSource
import java.io.File

abstract class JvmAppContainer(
    private val config: JvmAppConfig,
) : AppContainer() {
    val downloadsDir: File = config.downloadsDir
    override val fileServer: FileServer by lazy {
        FileServer(
            configuredPort = config.port,
            downloadsDir = downloadsDir,
            trustedDeviceStore = trustedDeviceStore,
            deviceKeyPair = config.deviceKeyPair,
            tracker = transferActivityTracker,
            deviceIdentityStore = deviceIdentityStore,
            discoveredDevicesStore = discoveredDevicesStore,
        )
    }

    // TODO(#193): replace with real Desktop file picker
    override val filePicker: FilePicker = object : FilePicker {
        override suspend fun pickFiles(): List<FileSource> = throw NotImplementedError("TODO(#193): Desktop file picker not yet implemented")
        override suspend fun pickFolder(): List<FileSource> = throw NotImplementedError("TODO(#193): Desktop file picker not yet implemented")
        override suspend fun pickPhotos(): List<FileSource> = throw UnsupportedOperationException("pickPhotos is not supported on Desktop")
    }
}
