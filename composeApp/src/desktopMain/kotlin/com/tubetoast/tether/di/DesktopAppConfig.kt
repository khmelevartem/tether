package com.tubetoast.tether.di

import com.tubetoast.tether.config.DeviceNamePersistence
import com.tubetoast.tether.identity.FingerprintPersistence
import com.tubetoast.tether.security.DeviceKeyPair
import java.io.File

interface DesktopAppConfig : JvmAppConfig {
    val preferencesFilePath: String
    val namePersistenceOverride: DeviceNamePersistence?
    val fingerprintPersistenceOverride: FingerprintPersistence?
}

class DefaultDesktopAppConfig(
    override val port: Int,
    override val downloadsDir: File = File(System.getProperty("user.home"), "Downloads/Tether"),
    configDir: File? = null,
    override val deviceKeyPair: DeviceKeyPair = if (configDir != null) DeviceKeyPair(configDir) else DeviceKeyPair(),
    override val namePersistenceOverride: DeviceNamePersistence? = null,
    override val fingerprintPersistenceOverride: FingerprintPersistence? = null,
) : DesktopAppConfig {
    override val preferencesFilePath: String = if (configDir != null) {
        File(configDir, "preferences.preferences_pb").absolutePath
    } else {
        resolvePreferencesFilePath()
    }
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
