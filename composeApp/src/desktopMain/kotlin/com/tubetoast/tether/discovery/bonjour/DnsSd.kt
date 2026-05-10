// JNA Library member names must match the native C symbols (DNSServiceBrowse etc),
// so the standard camelCase rule does not apply on this file.
@file:Suppress("ktlint:standard:function-naming", "FunctionName")

package com.tubetoast.tether.discovery.bonjour

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.PointerType
import com.sun.jna.ptr.PointerByReference

/**
 * JNA bindings for Apple's DNS-SD API (declarations from `/usr/include/dns_sd.h`,
 * implementation in libSystem on macOS). Only the symbols used by
 * [MdnsDiscoveryBonjour] are bound.
 *
 * On macOS the kernel routes incoming WiFi mDNS multicast exclusively to
 * `mDNSResponder` via a privileged path; user-space sockets joined to
 * `224.0.0.251:5353` do not receive these packets, which is why the JVM-host
 * implementation must go through this IPC API rather than reading multicast
 * directly. See issue #47 and `docs/knowledge/macos-mdns-bonjour.md`.
 */
internal interface DnsSd : Library {
    fun DNSServiceBrowse(
        sdRef: PointerByReference,
        flags: Int,
        interfaceIndex: Int,
        regtype: String,
        domain: String?,
        callBack: BrowseReply,
        context: Pointer?,
    ): Int

    fun DNSServiceResolve(
        sdRef: PointerByReference,
        flags: Int,
        interfaceIndex: Int,
        name: String,
        regtype: String,
        domain: String,
        callBack: ResolveReply,
        context: Pointer?,
    ): Int

    fun DNSServiceGetAddrInfo(
        sdRef: PointerByReference,
        flags: Int,
        interfaceIndex: Int,
        protocol: Int,
        hostname: String,
        callBack: GetAddrInfoReply,
        context: Pointer?,
    ): Int

    fun DNSServiceRegister(
        sdRef: PointerByReference,
        flags: Int,
        interfaceIndex: Int,
        name: String?,
        regtype: String,
        domain: String?,
        host: String?,
        port: Short,
        txtLen: Short,
        txtRecord: Pointer?,
        callBack: RegisterReply?,
        context: Pointer?,
    ): Int

    fun DNSServiceProcessResult(sdRef: Pointer): Int

    fun DNSServiceRefSockFD(sdRef: Pointer): Int

    fun DNSServiceRefDeallocate(sdRef: Pointer)

    interface BrowseReply : Callback {
        fun invoke(
            sdRef: Pointer,
            flags: Int,
            interfaceIndex: Int,
            errorCode: Int,
            serviceName: String?,
            regtype: String?,
            replyDomain: String?,
            context: Pointer?,
        )
    }

    interface ResolveReply : Callback {
        fun invoke(
            sdRef: Pointer,
            flags: Int,
            interfaceIndex: Int,
            errorCode: Int,
            fullname: String?,
            hosttarget: String?,
            port: Short,
            txtLen: Short,
            txtRecord: Pointer?,
            context: Pointer?,
        )
    }

    interface GetAddrInfoReply : Callback {
        fun invoke(
            sdRef: Pointer,
            flags: Int,
            interfaceIndex: Int,
            errorCode: Int,
            hostname: String?,
            address: SockAddr?,
            ttl: Int,
            context: Pointer?,
        )
    }

    interface RegisterReply : Callback {
        fun invoke(
            sdRef: Pointer,
            flags: Int,
            errorCode: Int,
            name: String?,
            regtype: String?,
            domain: String?,
            context: Pointer?,
        )
    }

    /**
     * Marker pointer for `sockaddr*` results from [DNSServiceGetAddrInfo]. We only
     * read the family byte and four IPv4 octets, so the full struct layout is not
     * defined here; see [BonjourCodec.readIpv4].
     */
    class SockAddr : PointerType()

    companion object {
        const val FLAGS_ADD: Int = 0x2
        const val PROTOCOL_IPV4: Int = 0x1
        const val AF_INET_BSD: Byte = 2
        const val NO_ERROR: Int = 0

        /**
         * Loaded only when the host OS is macOS — see `MdnsDiscovery.jvm.kt` dispatch.
         * The DNS-SD symbols live in libSystem (always-resident umbrella library);
         * Linux/Windows have no equivalent and never reach this initialiser.
         */
        val INSTANCE: DnsSd = Native.load("System", DnsSd::class.java)
    }
}
