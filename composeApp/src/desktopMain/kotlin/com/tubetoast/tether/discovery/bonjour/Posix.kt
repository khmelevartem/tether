package com.tubetoast.tether.discovery.bonjour

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native

/**
 * `poll(2)` from libSystem. Used by [MdnsDiscoveryBonjour] to wait for a DNS-SD
 * socket to be readable with a bounded timeout, so the polling coroutine can
 * observe cancellation without leaving `DNSServiceProcessResult` blocked across
 * a `DNSServiceRefDeallocate` call (Apple's `dns_sd.h` forbids that race).
 */
internal interface Posix : Library {
    fun poll(fds: Memory, nfds: Int, timeout: Int): Int

    companion object {
        /** `struct pollfd { int fd; short events; short revents; }` — 8 bytes on macOS. */
        const val POLLFD_SIZE: Long = 8
        const val OFFSET_FD: Long = 0
        const val OFFSET_EVENTS: Long = 4
        const val OFFSET_REVENTS: Long = 6

        const val POLLIN: Short = 0x0001
        const val POLLERR: Short = 0x0008
        const val POLLHUP: Short = 0x0010
        const val POLLNVAL: Short = 0x0020

        val INSTANCE: Posix = Native.load("System", Posix::class.java)
    }
}
