package com.tubetoast.tether.di

import android.app.Application
import android.os.Build
import com.tubetoast.tether.security.DeviceKeyPair
import com.tubetoast.tether.security.TrustedDeviceStore

class TetherAppConfig(
    override val application: Application,
) : AndroidAppConfig {
    override val deviceName: String = "Tether-${Build.MODEL}"
    override val port: Int = 0
    override val trustedDeviceStore: TrustedDeviceStore = TrustedDeviceStore(application)
    override val deviceKeyPair: DeviceKeyPair = DeviceKeyPair(application.filesDir)
}
