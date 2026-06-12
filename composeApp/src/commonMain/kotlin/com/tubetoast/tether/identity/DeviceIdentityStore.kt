package com.tubetoast.tether.identity

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

// TODO — replace interim random hex with EC P-256 public key fingerprint (#429)
class DeviceIdentityStore(
    private val persistence: FingerprintPersistence,
) {
    private val lock = Mutex()
    private var cached: String? = null

    suspend fun getOrCreate(): String = lock.withLock {
        cached?.let { return@withLock it }
        val existing = persistence.read()
        if (existing != null) {
            cached = existing
            return@withLock existing
        }
        val generated = Random.Default.nextBytes(16).toHexString()
        persistence.write(generated)
        cached = generated
        generated
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
