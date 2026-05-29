package com.tubetoast.tether.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tubetoast.tether.config.DefaultDeviceNamePersistence
import com.tubetoast.tether.config.DeviceNamePersistence
import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.identity.DeviceIdentityStore
import com.tubetoast.tether.preferences.DefaultFileTransferPreferences
import com.tubetoast.tether.preferences.DefaultPeerPreferencesStore
import com.tubetoast.tether.preferences.FileTransferPreferences
import com.tubetoast.tether.preferences.PeerPreferencesStore
import com.tubetoast.tether.protocol.DeviceType
import com.tubetoast.tether.security.DefaultTrustedDeviceStore
import com.tubetoast.tether.security.TrustedDeviceStore
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.io.File

class DesktopAppContainer(
    config: DesktopAppConfig,
) : JvmAppContainer(config) {
    private val dataStore = PreferenceDataStoreFactory.createWithPath {
        File(config.preferencesFilePath).also { it.parentFile?.mkdirs() }.absolutePath.toPath()
    }
    private val trustedDataStore = PreferenceDataStoreFactory.createWithPath {
        requireNotNull(File(config.preferencesFilePath).parentFile) {
            "preferencesFilePath must have a parent directory: ${config.preferencesFilePath}"
        }.resolve("tether_trusted_devices.preferences_pb")
            .also { it.parentFile?.mkdirs() }
            .absolutePath
            .toPath()
    }
    override val trustedDeviceStore: TrustedDeviceStore = DefaultTrustedDeviceStore(trustedDataStore)
    override val namePersistence: DeviceNamePersistence = config.namePersistenceOverride
        ?: DefaultDeviceNamePersistence(dataStore)
    override val deviceIdentityStore: DeviceIdentityStore = DeviceIdentityStore(dataStore)
    override val ownFingerprint: String = runBlocking { deviceIdentityStore.getOrCreate() }
    override val discoveredDevicesStore: DiscoveredDevicesStore = DiscoveredDevicesStore()
    override val mdnsDiscovery: MdnsDiscovery = MdnsDiscovery(discoveredDevicesStore, ownFingerprint)
    override val peerPreferencesStore: PeerPreferencesStore = DefaultPeerPreferencesStore(dataStore)
    override val fileTransferPreferences: FileTransferPreferences = DefaultFileTransferPreferences(
        dataStore = dataStore,
        defaultSaveLocation = File(System.getProperty("user.home"), "Downloads").absolutePath,
        saveLocationWritable = true,
    )
    override val ownDeviceType: DeviceType = DeviceType.Desktop
}
