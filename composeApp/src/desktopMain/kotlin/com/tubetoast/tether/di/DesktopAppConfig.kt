package com.tubetoast.tether.di

import com.tubetoast.tether.config.DeviceNamePersistence
import com.tubetoast.tether.security.DeviceKeyPair
import java.io.File

interface DesktopAppConfig : JvmAppConfig {
    val preferencesFilePath: String
    val namePersistenceOverride: DeviceNamePersistence?
}

class DefaultDesktopAppConfig(
    override val port: Int,
    override val downloadsDir: File = File(System.getProperty("user.home"), "Downloads/Tether"),
    override val deviceKeyPair: DeviceKeyPair = DeviceKeyPair(),
    override val namePersistenceOverride: DeviceNamePersistence? = null,
) : DesktopAppConfig {
    override val preferencesFilePath: String = resolvePreferencesFilePath()
}

private fun resolvePreferencesFilePath(): String {
    val home = System.getProperty("user.home")
    val os = System.getProperty("os.name", "").lowercase()
    val dir = when {
        os.contains("win") -> {
            val appData = System.getenv("APPDATA") ?: "$home\\AppData\\Roaming"
            File(appData, "Tether")
        }
        else -> File(home, ".config/tether")
    }
    return File(dir, "preferences.preferences_pb").absolutePath
}
