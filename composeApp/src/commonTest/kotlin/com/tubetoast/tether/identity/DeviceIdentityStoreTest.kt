package com.tubetoast.tether.identity

import com.tubetoast.tether.security.deviceIdFromPublicKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private val PUBKEY_A = ByteArray(32) { it.toByte() }
private val PUBKEY_B = ByteArray(32) { (it + 1).toByte() }

class DeviceIdentityStoreTest {
    @Test
    fun `fingerprint length is 64 hex chars`() {
        val fp = DeviceIdentityStore(PUBKEY_A).fingerprint()
        assertEquals(64, fp.length, "SHA-256 fingerprint must be 64 hex chars")
        assertTrue(fp.all { it in '0'..'9' || it in 'a'..'f' }, "must be lowercase hex: $fp")
    }

    @Test
    fun `fingerprint equals deviceIdFromPublicKey`() {
        val fp = DeviceIdentityStore(PUBKEY_A).fingerprint()
        assertEquals(deviceIdFromPublicKey(PUBKEY_A), fp)
    }

    @Test
    fun `fingerprint is idempotent`() {
        val store = DeviceIdentityStore(PUBKEY_A)
        assertEquals(store.fingerprint(), store.fingerprint())
    }

    @Test
    fun `two stores from same bytes yield equal fingerprint`() {
        assertEquals(
            DeviceIdentityStore(PUBKEY_A).fingerprint(),
            DeviceIdentityStore(PUBKEY_A).fingerprint(),
        )
    }

    @Test
    fun `two stores from different bytes yield different fingerprints`() {
        assertNotEquals(
            DeviceIdentityStore(PUBKEY_A).fingerprint(),
            DeviceIdentityStore(PUBKEY_B).fingerprint(),
        )
    }
}
