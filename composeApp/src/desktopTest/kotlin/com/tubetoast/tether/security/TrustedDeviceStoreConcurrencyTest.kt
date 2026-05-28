package com.tubetoast.tether.security

import com.tubetoast.tether.preferences.TempDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TrustedDeviceStoreConcurrencyTest {
    @Test
    fun `concurrent saveTrustedKey calls all persist`() = runTest {
        val temp = TempDataStore()
        val store = DefaultTrustedDeviceStore(temp.dataStore)
        val n = 50
        try {
            (0 until n)
                .map { i ->
                    async(Dispatchers.IO) {
                        store.saveTrustedKey(
                            "device-$i",
                            byteArrayOf(i.toByte(), (i + 1).toByte(), (i + 2).toByte()),
                        )
                    }
                }.awaitAll()
            for (i in 0 until n) {
                val key = store.getPublicKey("device-$i")
                assertNotNull(key, "device-$i must be present after concurrent writes")
                assertEquals(3, key.size)
                assertEquals(i.toByte(), key[0])
            }
        } finally {
            temp.tearDown()
        }
    }
}
