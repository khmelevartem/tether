package com.tubetoast.tether.di

import android.app.Application
import com.tubetoast.tether.security.DeviceKeyPair
import java.io.File

class TetherAppConfig(
    override val application: Application,
) : AndroidAppConfig {
    override val port: Int = 0
    override val downloadsDir: File =
        (application.getExternalFilesDir(null) ?: application.filesDir).resolve("Tether")
    override val deviceKeyPair: DeviceKeyPair = DeviceKeyPair(application.filesDir)
}
