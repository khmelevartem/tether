package com.tubetoast.tether.config

internal class InMemoryDeviceNamePersistence(
    private var stored: String? = null,
    private val readError: Throwable? = null,
    private val writeError: Throwable? = null,
) : DeviceNamePersistence {
    var writes = 0
        private set

    override suspend fun read(): String? {
        if (readError != null) throw readError
        return stored
    }

    override suspend fun write(value: String) {
        if (writeError != null) throw writeError
        stored = value
        writes++
    }
}
