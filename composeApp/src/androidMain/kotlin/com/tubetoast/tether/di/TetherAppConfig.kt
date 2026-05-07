package com.tubetoast.tether.di

import android.app.Application
import android.os.Build
import java.io.File

class TetherAppConfig(
    override val application: Application,
) : AndroidAppConfig {
    override val deviceName: String = "Tether-${Build.MODEL}"
    override val port: Int = 0
    override val downloadsDir: File =
        (application.getExternalFilesDir(null) ?: application.filesDir).resolve("Tether")
}
