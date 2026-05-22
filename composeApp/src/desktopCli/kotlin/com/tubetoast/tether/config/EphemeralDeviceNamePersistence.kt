package com.tubetoast.tether.config

class EphemeralDeviceNamePersistence : DeviceNamePersistence {
    override suspend fun read(): String? = null

    override suspend fun write(value: String) = Unit
}
