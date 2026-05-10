package com.tubetoast.tether.discovery.bonjour

import com.sun.jna.Pointer

/**
 * Pure helpers used by [MdnsDiscoveryBonjour]; extracted so they can be
 * unit-tested without loading libSystem.
 */
internal object BonjourCodec {
    /**
     * Convert a host-byte-order port (e.g. `8080`) to the network-byte-order
     * `Short` expected by `DNSServiceRegister`'s `port` parameter on macOS.
     */
    fun hostOrderToNetwork(port: Int): Short =
        ((((port and 0xFF) shl 8) or ((port ushr 8) and 0xFF))).toShort()

    /** Inverse of [hostOrderToNetwork]: network-byte-order `Short` to host-order `Int`. */
    fun networkOrderToHost(port: Short): Int {
        val unsigned = port.toInt() and 0xFFFF
        return ((unsigned and 0xFF) shl 8) or ((unsigned ushr 8) and 0xFF)
    }

    /**
     * Read an IPv4 dotted-quad from a BSD `sockaddr_in` pointer. Layout:
     * `{ u8 sa_len, u8 sa_family, u16 sin_port, u8 sin_addr[4], ... }`.
     *
     * Returns `null` when the pointer is null or the family byte is not BSD
     * `AF_INET` (2), so callers can quietly skip IPv6 reports.
     */
    fun readIpv4(sockaddr: Pointer?): String? {
        if (sockaddr == null) return null
        if (sockaddr.getByte(1) != DnsSd.AF_INET_BSD) return null
        val a = sockaddr.getByte(4).toInt() and 0xFF
        val b = sockaddr.getByte(5).toInt() and 0xFF
        val c = sockaddr.getByte(6).toInt() and 0xFF
        val d = sockaddr.getByte(7).toInt() and 0xFF
        return "$a.$b.$c.$d"
    }
}
