package com.tubetoast.tether.identity

interface FingerprintPersistence {
    suspend fun read(): String?

    suspend fun write(value: String)
}
