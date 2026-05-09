package com.tubetoast.tether.discovery.bonjour

import com.sun.jna.Memory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BonjourCodecTest {
    @Test
    fun `port byte order round-trips`() {
        // 8080 → 0x901F (network) → 8080 (host)
        val network = BonjourCodec.hostOrderToNetwork(8080)
        assertEquals(0x901F.toShort(), network, "8080 in network order")
        assertEquals(8080, BonjourCodec.networkOrderToHost(network), "round-trip")
    }

    @Test
    fun `port byte order known vectors`() {
        // sanity-check both directions on a couple of corner cases
        assertEquals(0x5000.toShort(), BonjourCodec.hostOrderToNetwork(80))
        assertEquals(80, BonjourCodec.networkOrderToHost(0x5000.toShort()))
        assertEquals(0xFFFF.toShort(), BonjourCodec.hostOrderToNetwork(0xFFFF))
        assertEquals(0xFFFF, BonjourCodec.networkOrderToHost(0xFFFF.toShort()))
    }

    @Test
    fun `readIpv4 parses sockaddr_in BSD layout`() {
        val mem = Memory(16)
        mem.setByte(0, 16) // sa_len
        mem.setByte(1, DnsSd.AF_INET_BSD) // sa_family = AF_INET
        mem.setShort(2, 0) // sin_port (ignored)
        mem.setByte(4, 192.toByte())
        mem.setByte(5, 168.toByte())
        mem.setByte(6, 1.toByte())
        mem.setByte(7, 138.toByte())
        assertEquals("192.168.1.138", BonjourCodec.readIpv4(mem))
    }

    @Test
    fun `readIpv4 returns null for null pointer`() {
        assertNull(BonjourCodec.readIpv4(null))
    }

    @Test
    fun `readIpv4 returns null for non-IPv4 family`() {
        val mem = Memory(28)
        mem.setByte(0, 28) // sa_len
        mem.setByte(1, 30) // AF_INET6 on macOS
        assertNull(BonjourCodec.readIpv4(mem))
    }
}
