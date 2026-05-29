package com.tubetoast.tether.di

import android.os.Environment
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tubetoast.tether.config.DefaultDeviceNamePersistence
import com.tubetoast.tether.config.DeviceNamePersistence
import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.identity.DeviceIdentityStore
import com.tubetoast.tether.network.AndroidTransferLockHolder
import com.tubetoast.tether.network.DefaultTransferActivityTracker
import com.tubetoast.tether.network.TransferActivityTracker
import com.tubetoast.tether.preferences.DefaultFileTransferPreferences
import com.tubetoast.tether.preferences.DefaultPeerPreferencesStore
import com.tubetoast.tether.preferences.FileTransferPreferences
import com.tubetoast.tether.preferences.PeerPreferencesStore
import com.tubetoast.tether.protocol.DeviceType
import com.tubetoast.tether.security.DefaultTrustedDeviceStore
import com.tubetoast.tether.security.TrustedDeviceStore
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath

class AndroidAppContainer(
    config: AndroidAppConfig,
) : JvmAppContainer(config) {
    val application = config.application
    private val lockHolder = AndroidTransferLockHolder(application)
    override val transferActivityTracker: TransferActivityTracker = DefaultTransferActivityTracker(
        onFirstEnter = lockHolder::acquire,
        onLastExit = lockHolder::release,
    )
    private val dataStore = PreferenceDataStoreFactory.createWithPath {
        application.filesDir
            .resolve("datastore/tether_preferences.preferences_pb")
            .also {
                it.parentFile?.mkdirs()
            }.toOkioPath()
    }
    private val trustedDataStore = PreferenceDataStoreFactory.createWithPath {
        application.filesDir
            .resolve("datastore/tether_trusted_devices.preferences_pb")
            .also {
                it.parentFile?.mkdirs()
            }.toOkioPath()
    }
    override val trustedDeviceStore: TrustedDeviceStore = DefaultTrustedDeviceStore(trustedDataStore)
    override val namePersistence: DeviceNamePersistence = DefaultDeviceNamePersistence(dataStore)
    override val deviceIdentityStore: DeviceIdentityStore = DeviceIdentityStore(dataStore)
    override val ownFingerprint: String = runBlocking { deviceIdentityStore.getOrCreate() }
    override val discoveredDevicesStore: DiscoveredDevicesStore = DiscoveredDevicesStore()
    override val mdnsDiscovery: MdnsDiscovery = MdnsDiscovery(application, discoveredDevicesStore, ownFingerprint)
    override val peerPreferencesStore: PeerPreferencesStore = DefaultPeerPreferencesStore(dataStore)
    override val fileTransferPreferences: FileTransferPreferences = DefaultFileTransferPreferences(
        dataStore = dataStore,
        defaultSaveLocation = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath ?: "",
        saveLocationWritable = true,
    )
    override val ownDeviceType: DeviceType = DeviceType.Mobile
}
