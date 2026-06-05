package com.tubetoast.tether.di

import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.tubetoast.tether.config.DefaultDeviceNamePersistence
import com.tubetoast.tether.config.DeviceNamePersistence
import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.discovery.MdnsDiscovery
import com.tubetoast.tether.identity.DataStoreFingerprintPersistence
import com.tubetoast.tether.identity.FingerprintPersistence
import com.tubetoast.tether.network.AndroidMediaStoreUploadStorage
import com.tubetoast.tether.network.AndroidTransferLockHolder
import com.tubetoast.tether.network.DefaultTransferActivityTracker
import com.tubetoast.tether.network.TransferActivityTracker
import com.tubetoast.tether.network.UploadStorage
import com.tubetoast.tether.preferences.DefaultFileTransferPreferences
import com.tubetoast.tether.preferences.FileTransferPreferences
import com.tubetoast.tether.protocol.DeviceType
import com.tubetoast.tether.transfer.AndroidFilePicker
import com.tubetoast.tether.transfer.AndroidPickerCoordinator
import com.tubetoast.tether.transfer.FilePicker
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
    override val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath {
        application.filesDir
            .resolve("datastore/tether_preferences.preferences_pb")
            .also {
                it.parentFile?.mkdirs()
            }.toOkioPath()
    }
    override val trustedDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath {
        application.filesDir
            .resolve("datastore/tether_trusted_devices.preferences_pb")
            .also {
                it.parentFile?.mkdirs()
            }.toOkioPath()
    }
    override val namePersistence: DeviceNamePersistence = DefaultDeviceNamePersistence(dataStore)
    override val fingerprintPersistence: FingerprintPersistence = DataStoreFingerprintPersistence(dataStore)
    override val discoveredDevicesStore: DiscoveredDevicesStore = DiscoveredDevicesStore()
    override val mdnsDiscovery: MdnsDiscovery by lazy {
        MdnsDiscovery(application, discoveredDevicesStore, deviceIdentityStore)
    }
    override val fileTransferPreferences: FileTransferPreferences = DefaultFileTransferPreferences(
        dataStore = dataStore,
        defaultSaveLocation = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath ?: "",
        saveLocationWritable = true,
    )
    override val ownDeviceType: DeviceType = DeviceType.Android

    internal override val uploadStorage: UploadStorage by lazy {
        AndroidMediaStoreUploadStorage(application.contentResolver)
    }

    val pickerCoordinator: AndroidPickerCoordinator = AndroidPickerCoordinator()

    val androidFilePicker: AndroidFilePicker = AndroidFilePicker(
        coordinator = pickerCoordinator,
        contentResolver = application.contentResolver,
        appContext = application,
    )

    override val filePicker: FilePicker = androidFilePicker
}
