package com.tubetoast.tether.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tubetoast.tether.config.DefaultDeviceNamePersistence
import com.tubetoast.tether.config.DeviceNamePersistence
import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.identity.DeviceIdentityStore
import com.tubetoast.tether.network.FileServer
import com.tubetoast.tether.preferences.DefaultFileTransferPreferences
import com.tubetoast.tether.preferences.DefaultPeerPreferencesStore
import com.tubetoast.tether.preferences.FileTransferPreferences
import com.tubetoast.tether.preferences.PeerPreferencesStore
import com.tubetoast.tether.protocol.DeviceType
import com.tubetoast.tether.security.DefaultTrustedDeviceStore
import com.tubetoast.tether.security.TrustedDeviceStore
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.runBlocking
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
    private val trustedDataStore = PreferenceDataStoreFactory.createWithPath {
        appSupportDir().toPath() / "tether_trusted_devices.preferences_pb"
    }
    override val trustedDeviceStore: TrustedDeviceStore = DefaultTrustedDeviceStore(trustedDataStore)
    override val deviceIdentityStore: DeviceIdentityStore = DeviceIdentityStore(dataStore)
    override val ownFingerprint: String = runBlocking { deviceIdentityStore.getOrCreate() }
    override val discoveredDevicesStore: DiscoveredDevicesStore = DiscoveredDevicesStore()
    override val fileServer: FileServer by lazy {
        FileServer(
            configuredPort = 0,
            trustedDeviceStore = trustedDeviceStore,
            deviceKeyPair = config.deviceKeyPair,
            tracker = transferActivityTracker,
            ownFingerprint = { ownFingerprint },
            discoveredDevicesStore = discoveredDevicesStore,
        )
    }
    override val mdnsDiscovery: MdnsDiscovery = MdnsDiscovery(discoveredDevicesStore, ownFingerprint)
    override val peerPreferencesStore: PeerPreferencesStore = DefaultPeerPreferencesStore(dataStore)
    override val fileTransferPreferences: FileTransferPreferences = DefaultFileTransferPreferences(
        dataStore = dataStore,
        defaultSaveLocation = documentsDir(),
        saveLocationWritable = false,
    )
    override val ownDeviceType: DeviceType = DeviceType.Mobile
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
