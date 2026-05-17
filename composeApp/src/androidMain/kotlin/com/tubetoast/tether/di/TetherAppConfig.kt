package com.tubetoast.tether.di

import android.app.Application
import android.os.Build
import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore
import java.io.File

class TetherAppConfig(
    override val application: Application,
) : AndroidAppConfig {
    override val deviceName: String = "Tether-${Build.MODEL}"
    override val port: Int = 0

    // downloadsDir satisfies the JvmAppConfig contract but is unused on Android:
    // AndroidAppContainer overrides fileServer with AndroidMediaStoreUploadStorage.
    override val downloadsDir: File = application.cacheDir
    override val trustedDeviceStore: TrustedDeviceStore = TrustedDeviceStore(application)
    override val deviceKeyPair: DeviceKeyPair = DeviceKeyPair(application.filesDir)
}
