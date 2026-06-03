package com.tubetoast.tether.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.tubetoast.tether.config.DefaultDeviceNamePersistence
import com.tubetoast.tether.config.DeviceNamePersistence
import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.identity.DataStoreFingerprintPersistence
import com.tubetoast.tether.identity.FingerprintPersistence
import com.tubetoast.tether.network.FileServer
import com.tubetoast.tether.preferences.DefaultFileTransferPreferences
import com.tubetoast.tether.preferences.FileTransferPreferences
import com.tubetoast.tether.protocol.DeviceType
import com.tubetoast.tether.transfer.FilePicker
import com.tubetoast.tether.transfer.FileSource
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

open class AppleAppContainer(
    private val config: AppleAppConfig,
) : AppContainer() {
    override val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath {
        appSupportDir().toPath() / "tether_preferences.preferences_pb"
    }
    override val trustedDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath {
        appSupportDir().toPath() / "tether_trusted_devices.preferences_pb"
    }
    override val namePersistence: DeviceNamePersistence = DefaultDeviceNamePersistence(dataStore)
    override val fingerprintPersistence: FingerprintPersistence = DataStoreFingerprintPersistence(dataStore)
    override val discoveredDevicesStore: DiscoveredDevicesStore = DiscoveredDevicesStore()
    override val fileServer: FileServer by lazy {
        FileServer(
            configuredPort = 0,
            trustedDeviceStore = trustedDeviceStore,
            deviceKeyPair = config.deviceKeyPair,
            tracker = transferActivityTracker,
            deviceIdentityStore = deviceIdentityStore,
            discoveredDevicesStore = discoveredDevicesStore,
        )
    }
    override val mdnsDiscovery: MdnsDiscovery by lazy {
        MdnsDiscovery(discoveredDevicesStore, deviceIdentityStore)
    }
    override val fileTransferPreferences: FileTransferPreferences = DefaultFileTransferPreferences(
        dataStore = dataStore,
        defaultSaveLocation = documentsDir(),
        saveLocationWritable = false,
    )
    override val ownDeviceType: DeviceType = DeviceType.Ios

    // TODO(#194): replace with real iOS file picker
    override val filePicker: FilePicker = object : FilePicker {
        override suspend fun pickFiles(): List<FileSource> = throw NotImplementedError("TODO(#194): iOS file picker not yet implemented")
        override suspend fun pickFolder(): List<FileSource> = throw NotImplementedError("TODO(#194): iOS file picker not yet implemented")
        override suspend fun pickPhotos(): List<FileSource> = throw NotImplementedError("TODO(#194): iOS photo picker not yet implemented")
    }
}

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
private fun appSupportDir(): String {
    val paths = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
    val dir = paths.firstOrNull() as? String ?: error("NSApplicationSupportDirectory unavailable")
    memScoped {
        val errorPtr = alloc<ObjCObjectVar<NSError?>>()
        val ok = NSFileManager.defaultManager.createDirectoryAtPath(
            dir,
            withIntermediateDirectories = true,
            attributes = null,
            error = errorPtr.ptr,
        )
        if (!ok) {
            val msg = errorPtr.value?.localizedDescription ?: "unknown error"
            println("[Tether] AppSupport dir create failed: $msg")
        }
    }
    return dir
}

@OptIn(ExperimentalForeignApi::class)
private fun documentsDir(): String {
    val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
    return paths.firstOrNull() as? String ?: ""
}
