package com.tubetoast.tether.discovery

import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.warn
import ru.pocketbyte.kydra.log.wrapper.withTag
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

private val log = KydraLog.withTag(default = "NetworkInterfaceProvider")

interface NetworkInterfaceProvider {
    fun bindAddresses(): List<Pair<String, InetAddress>>
}

class DefaultNetworkInterfaceProvider : NetworkInterfaceProvider {
    override fun bindAddresses(): List<Pair<String, InetAddress>> = try {
        NetworkInterface
            .getNetworkInterfaces()
            ?.asSequence()
            ?.filter { it.isUp && !it.isLoopback }
            ?.flatMap { networkInterface ->
                networkInterface.inetAddresses
                    .asSequence()
                    .filter { it is Inet4Address && !it.isLinkLocalAddress && !it.isLoopbackAddress }
                    .map { networkInterface.name to it }
            }?.toList()
            ?: emptyList()
    } catch (e: java.net.SocketException) {
        log.warn { "getNetworkInterfaces failed — ${e.message}" }
        emptyList()
    }
}
