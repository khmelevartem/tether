package com.tubetoast.tether.identity

class EphemeralFingerprintPersistence : FingerprintPersistence {
    private var value: String? = null

    override suspend fun read(): String? = value

    override suspend fun write(value: String) {
        this.value = value
    }
}
