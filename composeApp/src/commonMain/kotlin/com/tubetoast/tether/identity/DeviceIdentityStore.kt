package com.tubetoast.tether.identity

import kotlin.random.Random

// TODO — replace interim random hex with EC P-256 public key fingerprint (#11)
class DeviceIdentityStore(
    private val persistence: FingerprintPersistence,
) {
    suspend fun getOrCreate(): String {
        val existing = persistence.read()
        if (existing != null) return existing
        val generated = Random.Default.nextBytes(16).toHexString()
        persistence.write(generated)
        return generated
    }
}

private fun ByteArray.toHexString(): String =
    joinToString("") { byte ->
        byte
            .toInt()
            .and(0xFF)
            .toString(16)
            .padStart(2, '0')
    }
