package com.tubetoast.tether.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.tubetoast.tether.config.DefaultDeviceNamePersistence
import com.tubetoast.tether.config.DeviceNamePersistence
import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.preferences.DefaultFileTransferPreferences
import com.tubetoast.tether.preferences.FileTransferPreferences
import com.tubetoast.tether.protocol.DeviceType
import com.tubetoast.tether.transfer.BatchSender
import com.tubetoast.tether.transfer.PeerIdentity
import okio.Path.Companion.toPath
import java.io.File

class DesktopAppContainer(
    config: DesktopAppConfig,
    override val ownDeviceType: DeviceType = DeviceType.Desktop,
    batchSenderFactoryOverride: ((PeerIdentity) -> BatchSender)? = null,
) : JvmAppContainer(config) {
    override val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath {
        File(config.preferencesFilePath).also { it.parentFile?.mkdirs() }.absolutePath.toPath()
    }
    override val trustedDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath {
        requireNotNull(File(config.preferencesFilePath).parentFile) {
            "preferencesFilePath must have a parent directory: ${config.preferencesFilePath}"
        }.resolve("tether_trusted_devices.preferences_pb")
            .also { it.parentFile?.mkdirs() }
            .absolutePath
            .toPath()
    }
    override val namePersistence: DeviceNamePersistence = config.namePersistenceOverride
        ?: DefaultDeviceNamePersistence(dataStore)
    override val discoveredDevicesStore: DiscoveredDevicesStore = DiscoveredDevicesStore()
    override val mdnsDiscovery: MdnsDiscovery by lazy {
        MdnsDiscovery(discoveredDevicesStore, deviceIdentityStore)
    }
    override val fileTransferPreferences: FileTransferPreferences = DefaultFileTransferPreferences(
        dataStore = dataStore,
        defaultSaveLocation = File(System.getProperty("user.home"), "Downloads").absolutePath,
        saveLocationWritable = true,
    )
    override val batchSenderFactory: (PeerIdentity) -> BatchSender by lazy {
        batchSenderFactoryOverride ?: super.batchSenderFactory
    }
}
