package com.tubetoast.tether.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tubetoast.tether.config.DefaultDeviceNamePersistence
import com.tubetoast.tether.config.DeviceNamePersistence
import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.preferences.DefaultFileTransferPreferences
import com.tubetoast.tether.preferences.DefaultPeerPreferencesStore
import com.tubetoast.tether.preferences.FileTransferPreferences
import com.tubetoast.tether.preferences.PeerPreferencesStore
import com.tubetoast.tether.security.TrustedDeviceStore
import okio.Path.Companion.toPath
import java.io.File

class DesktopAppContainer(
    config: DesktopAppConfig,
) : JvmAppContainer(config) {
    override val trustedDeviceStore: TrustedDeviceStore = config.trustedDeviceStore
    private val dataStore = PreferenceDataStoreFactory.createWithPath {
        File(config.preferencesFilePath).also { it.parentFile?.mkdirs() }.absolutePath.toPath()
    }
    override val namePersistence: DeviceNamePersistence = config.namePersistenceOverride
        ?: DefaultDeviceNamePersistence(dataStore)
    override val mdnsDiscovery: MdnsDiscovery = MdnsDiscovery(DiscoveredDevicesStore())
    override val peerPreferencesStore: PeerPreferencesStore = DefaultPeerPreferencesStore(dataStore)
    override val fileTransferPreferences: FileTransferPreferences = DefaultFileTransferPreferences(
        dataStore = dataStore,
        defaultSaveLocation = File(System.getProperty("user.home"), "Downloads").absolutePath,
        saveLocationWritable = true,
    )
}
