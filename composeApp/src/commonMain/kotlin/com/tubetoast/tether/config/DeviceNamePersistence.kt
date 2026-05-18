package com.tubetoast.tether.config

interface DeviceNamePersistence {
    suspend fun read(): String?

    suspend fun write(value: String)
}
