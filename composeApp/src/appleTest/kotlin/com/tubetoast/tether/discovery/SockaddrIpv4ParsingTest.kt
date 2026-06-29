package com.tubetoast.tether.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SockaddrIpv4ParsingTest {
    private fun sockaddrIn(a: Int, b: Int, c: Int, d: Int): ByteArray {
        // Darwin sockaddr_in: sin_len(1) + sin_family(1=AF_INET) + sin_port(2) + sin_addr(4) + sin_zero(8)
        val bytes = ByteArray(16)
        bytes[0] = 16.toByte()
        bytes[1] = 2.toByte() // AF_INET
        bytes[2] = 0
        bytes[3] = 0
        bytes[4] = a.toByte()
        bytes[5] = b.toByte()
        bytes[6] = c.toByte()
        bytes[7] = d.toByte()
        return bytes
    }

    @Test
    fun `parses typical LAN address`() {
        val bytes = sockaddrIn(192, 168, 1, 42)
        assertEquals("192.168.1.42", sockaddrBytesToIpv4(bytes))
    }

    @Test
    fun `parses address with high octets requiring unsigned treatment`() {
        val bytes = sockaddrIn(10, 0, 255, 254)
        assertEquals("10.0.255.254", sockaddrBytesToIpv4(bytes))
    }

    @Test
    fun `returns null for AF_INET6 family byte`() {
        val bytes = sockaddrIn(0, 0, 0, 1)
        bytes[1] = 30.toByte() // AF_INET6 on Darwin
        assertNull(sockaddrBytesToIpv4(bytes))
    }

    @Test
    fun `returns null for buffer shorter than 8 bytes`() {
        assertNull(sockaddrBytesToIpv4(ByteArray(7)))
    }

    @Test
    fun `returns null for empty array`() {
        assertNull(sockaddrBytesToIpv4(ByteArray(0)))
    }
}
