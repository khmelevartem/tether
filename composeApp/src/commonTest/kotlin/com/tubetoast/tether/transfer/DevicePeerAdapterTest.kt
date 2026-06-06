package com.tubetoast.tether.transfer

import com.tubetoast.tether.protocol.Device
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DevicePeerAdapterTest {
    @Test
    fun `same fingerprint different port yields equal PeerIdentity`() {
        val devA = Device(name = "MyPhone", host = "192.168.1.10", port = 9000, fingerprint = "fp-abc")
        val devB = Device(name = "MyPhone", host = "192.168.1.10", port = 9001, fingerprint = "fp-abc")

        assertEquals(devA.toPeerIdentity(), devB.toPeerIdentity())
    }

    @Test
    fun `different fingerprint yields different PeerIdentity`() {
        val devA = Device(name = "MyPhone", host = "192.168.1.10", port = 9000, fingerprint = "fp-abc")
        val devB = Device(name = "MyPhone", host = "192.168.1.10", port = 9000, fingerprint = "fp-xyz")

        assertNotEquals(devA.toPeerIdentity(), devB.toPeerIdentity())
    }

    @Test
    fun `null fingerprint falls back to id`() {
        val dev = Device(name = "MyPhone", host = "192.168.1.10", port = 9000, fingerprint = null)

        assertEquals(PeerIdentity(dev.id), dev.toPeerIdentity())
    }
}
