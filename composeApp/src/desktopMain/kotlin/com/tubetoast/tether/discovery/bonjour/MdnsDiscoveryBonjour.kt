package com.tubetoast.tether.discovery.bonjour

import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

// JVM-on-macOS implementation backed by Apple's DNS-SD API (mDNSResponder IPC).
//
// On macOS the kernel routes incoming WiFi mDNS multicast packets exclusively to
// mDNSResponder, so user-space sockets bound to 224.0.0.251:5353 (e.g. JmDNS) do not see
// announcements from external peers — only the loopback path delivers to user space.
// Going through DNS-SD makes us a peer of mDNSResponder rather than a competing
// multicast listener, which is the only way to observe external peers reliably.
//
// Discovery is staged: Browse → Resolve → GetAddrInfo. Each stage opens its own
// DNSServiceRef and runs a blocking DNSServiceProcessResult loop on Dispatchers.IO;
// callbacks marshal events onto a single events Channel where mutable state (devices
// list, sub-session refs) is mutated by exactly one consumer coroutine without locks.
internal class MdnsDiscoveryBonjour {
    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    val discoveredDevices: StateFlow<List<Device>> = _discoveredDevices.asStateFlow()

    private val lifecycleLock = Any()

    @Volatile private var session: Session? = null

    fun start(deviceName: String, port: Int) {
        synchronized(lifecycleLock) {
            if (session != null) throw IllegalStateException("MdnsDiscovery already started; call stop() first")
            session = Session.start(deviceName, port, _discoveredDevices)
        }
    }

    fun stop() {
        val toClose = synchronized(lifecycleLock) {
            val current = session
            session = null
            current
        }
        toClose?.close()
        _discoveredDevices.value = emptyList()
    }

    private class Session(
        private val ownName: String,
        private val ownPort: Int,
        private val discoveredDevices: MutableStateFlow<List<Device>>,
        private val scope: CoroutineScope,
        private val registerRef: Pointer?,
        private val browseRef: Pointer,
        private val events: Channel<Event>,
    ) {
        // Concurrent maps because the events-consumer coroutine and stop() may run on
        // different threads. ConcurrentHashMap prevents CME during stop()'s forEach over
        // values while a final callback is still in flight.
        private val resolveRefs = ConcurrentHashMap<String, Pointer>()
        private val addrInfoRefs = ConcurrentHashMap<String, Pointer>()
        private val pendingPorts = ConcurrentHashMap<String, Int>()
        private val pendingIps = ConcurrentHashMap<String, String>()

        // Anchor JNA callbacks against GC for the session lifetime. Without strong
        // references, JNA's CallbackReference can free the trampoline while
        // mDNSResponder still holds a pointer to it.
        private val callbackAnchors = CopyOnWriteArrayList<Any>()

        fun close() {
            scope.cancel()
            events.close()
            deallocate(browseRef, "browse")
            registerRef?.let { deallocate(it, "register") }
            (resolveRefs.values + addrInfoRefs.values).forEach { deallocate(it, "sub") }
            resolveRefs.clear()
            addrInfoRefs.clear()
            pendingPorts.clear()
            pendingIps.clear()
            callbackAnchors.clear()
        }

        fun trackAnchor(callback: Any) {
            callbackAnchors.add(callback)
        }

        suspend fun consumeEvents() {
            for (event in events) {
                when (event) {
                    is Event.BrowseAdd -> onBrowseAdd(event)
                    is Event.BrowseRemove -> onBrowseRemove(event)
                    is Event.Resolved -> onResolved(event)
                    is Event.AddrInfoFound -> onAddrInfoFound(event)
                }
            }
        }

        suspend fun processLoop(ref: Pointer) {
            while (currentCoroutineContext().isActive) {
                val rc = try {
                    DnsSd.INSTANCE.DNSServiceProcessResult(ref)
                } catch (_: Throwable) {
                    return
                }
                if (rc != DnsSd.NO_ERROR) return
            }
        }

        private fun onBrowseAdd(event: Event.BrowseAdd) {
            if (event.name == ownName) return
            if (resolveRefs.containsKey(event.name)) return
            val ref = openResolve(event.name, event.interfaceIndex) ?: return
            resolveRefs[event.name] = ref
            scope.launch(Dispatchers.IO) { processLoop(ref) }
        }

        private fun onBrowseRemove(event: Event.BrowseRemove) {
            if (event.name == ownName) return
            resolveRefs.remove(event.name)?.let { deallocate(it, "resolve(removed)") }
            addrInfoRefs.remove(event.name)?.let { deallocate(it, "addrInfo(removed)") }
            pendingPorts.remove(event.name)
            pendingIps.remove(event.name)
            discoveredDevices.value = discoveredDevices.value.filterNot { it.name == event.name }
        }

        private fun onResolved(event: Event.Resolved) {
            if (event.name == ownName) return
            pendingPorts[event.name] = event.port
            // The resolve session stays alive across SRV changes (e.g. a peer restarts on
            // a different port) and fires this callback again with the new port. If the IP
            // is still known from a prior addrinfo result, re-emit the device immediately;
            // otherwise wait for the addrinfo callback below.
            emitDeviceIfReady(event.name)
            if (addrInfoRefs.containsKey(event.name)) return
            val ref = openGetAddrInfo(event.name, event.host) ?: return
            addrInfoRefs[event.name] = ref
            scope.launch(Dispatchers.IO) { processLoop(ref) }
        }

        private fun onAddrInfoFound(event: Event.AddrInfoFound) {
            if (event.name == ownName) return
            pendingIps[event.name] = event.ipv4
            emitDeviceIfReady(event.name)
        }

        private fun emitDeviceIfReady(name: String) {
            val ip = pendingIps[name] ?: return
            val port = pendingPorts[name] ?: return
            val device = Device(
                id = "$name@$ip:$port",
                name = name,
                host = ip,
                port = port,
            )
            discoveredDevices.value = discoveredDevices.value
                .filterNot { it.name == name } + device
        }

        private fun openResolve(name: String, interfaceIndex: Int): Pointer? {
            val ref = PointerByReference()
            val callback = object : DnsSd.ResolveReply {
                override fun invoke(
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
                ) {
                    if (errorCode != DnsSd.NO_ERROR) return
                    val host = hosttarget ?: return
                    val portHostOrder = networkOrderToHost(port)
                    events.trySend(Event.Resolved(name, host, portHostOrder))
                }
            }
            trackAnchor(callback)
            val rc = DnsSd.INSTANCE.DNSServiceResolve(
                ref,
                0,
                interfaceIndex,
                name,
                SERVICE_TYPE,
                "local.",
                callback,
                null,
            )
            if (rc != DnsSd.NO_ERROR) {
                System.err.println("WARN: DNSServiceResolve('$name') failed rc=$rc")
                return null
            }
            return ref.value
        }

        private fun openGetAddrInfo(peerName: String, hostname: String): Pointer? {
            val ref = PointerByReference()
            val callback = object : DnsSd.GetAddrInfoReply {
                override fun invoke(
                    sdRef: Pointer,
                    flags: Int,
                    interfaceIndex: Int,
                    errorCode: Int,
                    hostname: String?,
                    address: DnsSd.SockAddr?,
                    ttl: Int,
                    context: Pointer?,
                ) {
                    if (errorCode != DnsSd.NO_ERROR) return
                    val ipv4 = readIpv4(address?.pointer) ?: return
                    events.trySend(Event.AddrInfoFound(peerName, ipv4))
                }
            }
            trackAnchor(callback)
            val rc = DnsSd.INSTANCE.DNSServiceGetAddrInfo(
                ref,
                0,
                0,
                DnsSd.PROTOCOL_IPV4,
                hostname,
                callback,
                null,
            )
            if (rc != DnsSd.NO_ERROR) {
                System.err.println("WARN: DNSServiceGetAddrInfo('$hostname') failed rc=$rc")
                return null
            }
            return ref.value
        }

        sealed class Event {
            data class BrowseAdd(
                val name: String,
                val interfaceIndex: Int,
            ) : Event()

            data class BrowseRemove(
                val name: String,
            ) : Event()

            data class Resolved(
                val name: String,
                val host: String,
                val port: Int,
            ) : Event()

            data class AddrInfoFound(
                val name: String,
                val ipv4: String,
            ) : Event()
        }

        companion object {
            const val SERVICE_TYPE: String = "_tether._tcp."

            fun start(
                deviceName: String,
                port: Int,
                discoveredDevices: MutableStateFlow<List<Device>>,
            ): Session {
                val events = Channel<Event>(Channel.UNLIMITED)
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                val anchors = mutableListOf<Any>()

                val registerRef = openRegister(deviceName, port, anchors)

                val browseCallback = object : DnsSd.BrowseReply {
                    override fun invoke(
                        sdRef: Pointer,
                        flags: Int,
                        interfaceIndex: Int,
                        errorCode: Int,
                        serviceName: String?,
                        regtype: String?,
                        replyDomain: String?,
                        context: Pointer?,
                    ) {
                        if (errorCode != DnsSd.NO_ERROR) return
                        val name = serviceName ?: return
                        val event = if ((flags and DnsSd.FLAGS_ADD) != 0) {
                            Event.BrowseAdd(name, interfaceIndex)
                        } else {
                            Event.BrowseRemove(name)
                        }
                        events.trySend(event)
                    }
                }
                anchors.add(browseCallback)

                val browseRefPtr = PointerByReference()
                val rc = DnsSd.INSTANCE.DNSServiceBrowse(
                    browseRefPtr,
                    0,
                    0,
                    SERVICE_TYPE,
                    null,
                    browseCallback,
                    null,
                )
                if (rc != DnsSd.NO_ERROR) {
                    registerRef?.let { deallocate(it, "register(rollback)") }
                    scope.cancel()
                    events.close()
                    throw IllegalStateException("DNSServiceBrowse failed rc=$rc")
                }
                val browseRef = browseRefPtr.value
                    ?: throw IllegalStateException("DNSServiceBrowse returned null sdRef")

                val session = Session(
                    ownName = deviceName,
                    ownPort = port,
                    discoveredDevices = discoveredDevices,
                    scope = scope,
                    registerRef = registerRef,
                    browseRef = browseRef,
                    events = events,
                )
                anchors.forEach { session.trackAnchor(it) }

                scope.launch(Dispatchers.IO) { session.processLoop(browseRef) }
                registerRef?.let { ref ->
                    scope.launch(Dispatchers.IO) { session.processLoop(ref) }
                }
                scope.launch { session.consumeEvents() }
                return session
            }

            private fun openRegister(
                deviceName: String,
                port: Int,
                anchors: MutableList<Any>,
            ): Pointer? {
                val ref = PointerByReference()
                val callback = object : DnsSd.RegisterReply {
                    override fun invoke(
                        sdRef: Pointer,
                        flags: Int,
                        errorCode: Int,
                        name: String?,
                        regtype: String?,
                        domain: String?,
                        context: Pointer?,
                    ) {
                        if (errorCode != DnsSd.NO_ERROR) {
                            System.err.println("WARN: DNSServiceRegister callback errorCode=$errorCode")
                        }
                    }
                }
                anchors.add(callback)
                val rc = DnsSd.INSTANCE.DNSServiceRegister(
                    ref,
                    0,
                    0,
                    deviceName,
                    SERVICE_TYPE,
                    null,
                    null,
                    hostOrderToNetwork(port),
                    0,
                    null,
                    callback,
                    null,
                )
                if (rc != DnsSd.NO_ERROR) {
                    System.err.println("WARN: DNSServiceRegister failed rc=$rc; running browse-only")
                    return null
                }
                return ref.value
            }

            private fun deallocate(ref: Pointer, label: String) {
                try {
                    DnsSd.INSTANCE.DNSServiceRefDeallocate(ref)
                } catch (e: Throwable) {
                    System.err.println("WARN: Bonjour $label deallocate failed — ${e.message}")
                }
            }

            private fun hostOrderToNetwork(port: Int): Short =
                ((((port and 0xFF) shl 8) or ((port ushr 8) and 0xFF))).toShort()

            private fun networkOrderToHost(port: Short): Int {
                val unsigned = port.toInt() and 0xFFFF
                return ((unsigned and 0xFF) shl 8) or ((unsigned ushr 8) and 0xFF)
            }

            private fun readIpv4(sockaddr: Pointer?): String? {
                if (sockaddr == null) return null
                // sockaddr_in (BSD): u8 sa_len, u8 sa_family, u16 sin_port, u8 sin_addr[4], ...
                if (sockaddr.getByte(1) != DnsSd.AF_INET_BSD) return null
                val a = sockaddr.getByte(4).toInt() and 0xFF
                val b = sockaddr.getByte(5).toInt() and 0xFF
                val c = sockaddr.getByte(6).toInt() and 0xFF
                val d = sockaddr.getByte(7).toInt() and 0xFF
                return "$a.$b.$c.$d"
            }
        }
    }
}
