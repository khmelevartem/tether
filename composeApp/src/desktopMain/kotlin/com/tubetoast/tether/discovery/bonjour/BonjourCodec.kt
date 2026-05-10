package com.tubetoast.tether.discovery.bonjour

import com.sun.jna.Pointer

/** Pure helpers; extracted to allow unit tests without loading libSystem. */
internal object BonjourCodec {
    fun hostOrderToNetwork(port: Int): Short =
        ((((port and 0xFF) shl 8) or ((port ushr 8) and 0xFF))).toShort()

    fun networkOrderToHost(port: Short): Int {
        val unsigned = port.toInt() and 0xFFFF
        return ((unsigned and 0xFF) shl 8) or ((unsigned ushr 8) and 0xFF)
    }

    /**
     * BSD `sockaddr_in` layout: `{ u8 sa_len, u8 sa_family, u16 sin_port, u8 sin_addr[4], ... }`.
     * Returns `null` for null pointer or non-`AF_INET` (2) family.
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
