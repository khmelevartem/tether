package com.tubetoast.tether.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.tubetoast.tether.config.DefaultDeviceNamePersistence
import com.tubetoast.tether.config.DeviceNamePersistence
import com.tubetoast.tether.discovery.DeviceDiscovery
import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.discovery.MdnsDiscoveryJmdns
import com.tubetoast.tether.discovery.bonjour.MdnsDiscoveryBonjour
import com.tubetoast.tether.foundation.DesktopHostOs
import com.tubetoast.tether.foundation.currentHostOs
import com.tubetoast.tether.identity.DataStoreFingerprintPersistence
import com.tubetoast.tether.identity.DeviceIdentityStore
import com.tubetoast.tether.identity.FingerprintPersistence
import com.tubetoast.tether.preferences.DefaultFileTransferPreferences
import com.tubetoast.tether.preferences.FileTransferPreferences
import com.tubetoast.tether.protocol.DeviceType
import com.tubetoast.tether.transfer.FilePicker
import com.tubetoast.tether.transfer.LinuxFilePicker
import com.tubetoast.tether.transfer.MacFilePicker
import com.tubetoast.tether.transfer.WindowHolder
import com.tubetoast.tether.transfer.WindowsFilePicker
import kotlinx.coroutines.Dispatchers
import okio.Path.Companion.toPath
import java.io.File

open class DesktopAppContainer(
    config: DesktopAppConfig,
    override val ownDeviceType: DeviceType = DeviceType.Desktop,
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
    override val fingerprintPersistence: FingerprintPersistence = config.fingerprintPersistenceOverride
        ?: DataStoreFingerprintPersistence(dataStore)
    override val discoveredDevicesStore: DiscoveredDevicesStore = DiscoveredDevicesStore()
    override val mdnsDiscovery: MdnsDiscovery by lazy {
        MdnsDiscovery(desktopMdnsDelegate(discoveredDevicesStore, deviceIdentityStore))
    }
    override val fileTransferPreferences: FileTransferPreferences = DefaultFileTransferPreferences(
        dataStore = dataStore,
        defaultSaveLocation = File(System.getProperty("user.home"), "Downloads").absolutePath,
        saveLocationWritable = true,
    )

    open val windowHolder: WindowHolder = WindowHolder()
    open override val filePicker: FilePicker = when (currentHostOs) {
        DesktopHostOs.MacOs -> MacFilePicker(windowHolder)
        DesktopHostOs.Windows -> WindowsFilePicker(windowHolder)
        DesktopHostOs.Linux -> LinuxFilePicker(windowHolder)
    }
}

/**
 * macOS → [MdnsDiscoveryBonjour] (DNS-SD IPC; JmDNS can't see external WiFi peers on macOS).
 * Linux/Windows → [MdnsDiscoveryJmdns].
 */
internal fun desktopMdnsDelegate(
    store: DiscoveredDevicesStore,
    deviceIdentityStore: DeviceIdentityStore,
): DeviceDiscovery = when (currentHostOs) {
    DesktopHostOs.MacOs -> MdnsDiscoveryBonjour(store, deviceIdentityStore)
    DesktopHostOs.Windows,
    DesktopHostOs.Linux,
    -> MdnsDiscoveryJmdns(store, Dispatchers.IO, deviceIdentityStore)
}
