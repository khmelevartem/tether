package com.tubetoast.tether.config

import java.util.prefs.Preferences

private const val KEY = "device_name"

class DeviceNamePersistenceJvm(
    private val prefs: Preferences = Preferences.userRoot().node("tether"),
) : DeviceNamePersistence {
    override suspend fun read(): String? = prefs.get(KEY, null)

    override suspend fun write(value: String) {
        prefs.put(KEY, value)
        prefs.flush()
    }
}
