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
    override val preferencesFilePath: String = if (configDir != null) {
        File(configDir, "preferences.preferences_pb").absolutePath
    } else {
        resolvePreferencesFilePath(desktopPlatform)
    },
) : DesktopAppConfig

private fun resolvePreferencesFilePath(desktopPlatform: DesktopPlatform): String {
    val dir = desktopPlatform.preferencesDir(System.getProperty("user.home"))
    return File(dir, "preferences.preferences_pb").absolutePath
}
