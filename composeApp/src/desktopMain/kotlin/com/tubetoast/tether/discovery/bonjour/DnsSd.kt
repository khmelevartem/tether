// JNA Library requires Kotlin function names to match the native C symbols (DNSServiceBrowse etc),
// so the standard camelCase rule does not apply here.
@file:Suppress("ktlint:standard:function-naming", "FunctionName")

package com.tubetoast.tether.discovery.bonjour

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.PointerType
import com.sun.jna.ptr.PointerByReference

// Thin JNA layer over Apple's DNS-SD API (libdns_sd / libSystem on macOS).
// Reference: /usr/include/dns_sd.h. Only the symbols we use are bound.
//
// Why this exists: on macOS, the kernel routes incoming WiFi mDNS multicast exclusively to
// mDNSResponder via a privileged path (BPF / kernel control socket). User-space sockets
// joined to 224.0.0.251:5353 do not receive these packets. The IPC path through DNS-SD is
// the only way to observe external peers from a user-space JVM process — see issue #47.
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

    // Marker pointer wrapper for sockaddr* — we only read the family byte and IPv4 octets,
    // so we never define the full struct layout.
    class SockAddr : PointerType()

    companion object {
        // DNSServiceFlags
        const val FLAGS_ADD: Int = 0x2

        // DNSServiceProtocol
        const val PROTOCOL_IPV4: Int = 0x1

        // sa_family values
        const val AF_INET_BSD: Byte = 2

        const val NO_ERROR: Int = 0

        // On macOS the DNS-SD symbols live in libSystem (an umbrella library that's always
        // loaded). On Linux/Windows there is no such API, so this class is only ever
        // initialised when the host OS is macOS — see MdnsDiscovery.jvm.kt dispatch.
        val INSTANCE: DnsSd = Native.load("System", DnsSd::class.java)
    }
}
