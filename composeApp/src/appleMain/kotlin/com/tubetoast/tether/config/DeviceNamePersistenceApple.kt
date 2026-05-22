package com.tubetoast.tether.config

import com.tubetoast.tether.foundation.writeOrThrow
import platform.Foundation.NSUserDefaults

private const val KEY = "tether_device_name"

class DeviceNamePersistenceApple(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : DeviceNamePersistence {
    override suspend fun read(): String? = defaults.stringForKey(KEY)

    override suspend fun write(value: String) {
        defaults.writeOrThrow(KEY, value)
    }
}
