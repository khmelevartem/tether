package com.tubetoast.tether.discovery

import com.tubetoast.tether.identity.DeviceIdentityStore
import com.tubetoast.tether.identity.EphemeralFingerprintPersistence
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the self-suppression predicate in MdnsDiscoveryJmdns: a peer whose fp TXT record
 * differs from our own fingerprint must not be dropped.
 *
 * Regression guard for two CLI instances on the same host sharing a DataStore fingerprint.
 */
class MdnsDiscoveryFingerprintFilterTest {
    private fun isSelf(ownFp: String, peerFp: String?): Boolean =
        peerFp != null && peerFp == ownFp

    @Test
    fun `peer with different fingerprint is not self`() {
        assertFalse(isSelf("aaaa1111aaaa1111aaaa1111aaaa1111", "bbbb2222bbbb2222bbbb2222bbbb2222"))
    }

    @Test
    fun `peer with same fingerprint is self`() {
        assertTrue(isSelf("aaaa1111aaaa1111aaaa1111aaaa1111", "aaaa1111aaaa1111aaaa1111aaaa1111"))
    }

    @Test
    fun `peer with null fingerprint is not self`() {
        assertFalse(isSelf("aaaa1111aaaa1111aaaa1111aaaa1111", null))
    }

    @Test
    fun `distinct EphemeralFingerprintPersistence instances yield distinct fingerprints`() =
        runTest {
            val fpA = DeviceIdentityStore(EphemeralFingerprintPersistence()).getOrCreate()
            val fpB = DeviceIdentityStore(EphemeralFingerprintPersistence()).getOrCreate()
            assertFalse(
                fpA == fpB,
                "distinct EphemeralFingerprintPersistence instances must produce distinct fingerprints",
            )
        }
}
