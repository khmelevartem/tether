package com.tubetoast.tether.identity

import kotlinx.coroutines.yield

internal class InMemoryFingerprintPersistence(
    private var stored: String? = null,
) : FingerprintPersistence {
    var writes = 0
        private set

    override suspend fun read(): String? {
        // Yield so concurrent coroutines can interleave inside the lock acquisition window — without
        // a real suspension point the cooperative test scheduler would let the first caller finish
        // its withLock body before any sibling enters, making the mutex contention untestable.
        yield()
        return stored
    }

    override suspend fun write(value: String) {
        stored = value
        writes++
    }
}
