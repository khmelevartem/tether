package com.tubetoast.tether.identity

class EphemeralFingerprintPersistence : FingerprintPersistence {
    override suspend fun read(): String? = null

    override suspend fun write(value: String) = Unit
}
