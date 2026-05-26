package com.tubetoast.tether.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tubetoast.tether.config.DefaultDeviceNamePersistence
import com.tubetoast.tether.config.DeviceNamePersistence
import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.network.FileServer
import com.tubetoast.tether.preferences.DefaultFileTransferPreferences
import com.tubetoast.tether.preferences.DefaultPeerPreferencesStore
import com.tubetoast.tether.preferences.FileTransferPreferences
import com.tubetoast.tether.preferences.PeerPreferencesStore
import com.tubetoast.tether.security.TrustedDeviceStore
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
    private val dataStore = PreferenceDataStoreFactory.createWithPath {
        appSupportDir().toPath() / "tether_preferences.preferences_pb"
    }
    override val namePersistence: DeviceNamePersistence = DefaultDeviceNamePersistence(dataStore)
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
    override val peerPreferencesStore: PeerPreferencesStore = DefaultPeerPreferencesStore(dataStore)
    override val fileTransferPreferences: FileTransferPreferences = DefaultFileTransferPreferences(
        dataStore = dataStore,
        defaultSaveLocation = documentsDir(),
        saveLocationWritable = false,
    )
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
