package com.tubetoast.tether.discovery

import com.tubetoast.tether.identity.DeviceIdentityStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** MdnsDiscoveryJmdns suppresses peers whose fp TXT matches own fingerprint, and only those. */
class MdnsDiscoveryFingerprintFilterTest {
    private fun discovery(identityStore: DeviceIdentityStore): MdnsDiscoveryJmdns {
        val store = DiscoveredDevicesStore()
        val fakeProvider = FakeNetworkInterfaceProvider(emptyList())
        return MdnsDiscoveryJmdns(store, Dispatchers.IO, identityStore, fakeProvider)
    }

    @Test
    fun `peer with matching fingerprint is suppressed`() = runTest {
        val identityStore = DeviceIdentityStore(ByteArray(32) { it.toByte() })
        val d = discovery(identityStore)
        d.start("Self", 29600)
        try {
            assertTrue(
                d.isOwnAnnounce(identityStore.fingerprint()),
                "peer with matching fingerprint must be suppressed",
            )
        } finally {
            d.stop()
        }
    }

    @Test
    fun `peer with different fingerprint is not suppressed`() = runTest {
        val d = discovery(DeviceIdentityStore(ByteArray(32) { it.toByte() }))
        d.start("Self", 29601)
        try {
            assertFalse(
                d.isOwnAnnounce("bbbb2222bbbb2222bbbb2222bbbb2222bbbb2222bbbb2222bbbb2222bbbb2222"),
                "peer with different fingerprint must not be suppressed",
            )
        } finally {
            d.stop()
        }
    }

    @Test
    fun `peer with null fingerprint is not suppressed`() = runTest {
        val d = discovery(DeviceIdentityStore(ByteArray(32) { it.toByte() }))
        d.start("Self", 29602)
        try {
            assertFalse(
                d.isOwnAnnounce(null),
                "peer with null fingerprint must not be suppressed",
            )
        } finally {
            d.stop()
        }
    }
}
