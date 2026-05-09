package com.tubetoast.tether.discovery.bonjour

import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

// JVM-on-macOS implementation backed by Apple's DNS-SD API (mDNSResponder IPC).
//
// On macOS the kernel routes incoming WiFi mDNS multicast exclusively to mDNSResponder,
// so user-space sockets bound to 224.0.0.251:5353 (e.g. JmDNS) do not see announcements
// from external peers — only the loopback path delivers to user space. Going through
// DNS-SD makes us a peer of mDNSResponder rather than a competing multicast listener,
// which is the only way to observe external peers reliably. See issue #47.
//
// Lifecycle invariant: each DNSServiceRef is owned by exactly one polling coroutine
// that calls DNSServiceProcessResult only when poll(2) reports the FD readable, and
// calls DNSServiceRefDeallocate in its own finally block once cancelled. This avoids
// the race forbidden by dns_sd.h ("no other thread is currently using the DNSServiceRef
// while DNSServiceRefDeallocate is being called"). Cancellation flows via standard
// kotlinx Job cancellation; the polling coroutine returns from poll within at most
// POLL_TIMEOUT_MS so close() never blocks the caller for long.
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
        private val discoveredDevices: MutableStateFlow<List<Device>>,
        private val scope: CoroutineScope,
        private val events: Channel<Event>,
    ) : BonjourState.Sink {
        private lateinit var state: BonjourState

        // Per-name jobs. Cancelling the job stops the poll loop and triggers
        // self-deallocation of the DNSServiceRef in the loop's finally block.
        private val resolveJobs = ConcurrentHashMap<String, Job>()
        private val addrInfoJobs = ConcurrentHashMap<String, Job>()

        // Anchor JNA callbacks against GC for the session lifetime. Without strong
        // references, JNA's CallbackReference can free the trampoline while
        // mDNSResponder still holds a pointer to it.
        val callbackAnchors = CopyOnWriteArrayList<Any>()

        fun close() {
            // 1. Stop accepting new events. The consumer coroutine drains its queue
            //    and exits once the channel is empty + closed.
            events.close()
            // 2. Cancel everything and wait for poll loops to exit. Each loop's finally
            //    deallocates its own ref, satisfying the dns_sd.h same-thread invariant.
            //    cancelAndJoin blocks the caller for at most POLL_TIMEOUT_MS.
            runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
            resolveJobs.clear()
            addrInfoJobs.clear()
            callbackAnchors.clear()
        }

        // BonjourState.Sink — events flow on the consumer coroutine, which is single-threaded
        // (Channel is consumed by exactly one launch{} below), so concurrent access to the
        // job maps stays bounded.
        override fun openResolve(name: String, interfaceIndex: Int) {
            resolveJobs[name] = scope.launch(Dispatchers.IO) { runResolve(name, interfaceIndex) }
        }

        override fun closeResolve(name: String) {
            resolveJobs.remove(name)?.cancel()
        }

        override fun openAddrInfo(name: String, hostname: String) {
            addrInfoJobs[name] = scope.launch(Dispatchers.IO) { runAddrInfo(name, hostname) }
        }

        override fun closeAddrInfo(name: String) {
            addrInfoJobs.remove(name)?.cancel()
        }

        override fun publishDevices(devices: List<Device>) {
            discoveredDevices.value = devices
        }

        private fun bindState(state: BonjourState) {
            this.state = state
        }

        private suspend fun consumeEvents() {
            for (event in events) {
                when (event) {
                    is Event.OwnNameAssigned -> state.ownNameAssigned(event.canonicalName)
                    is Event.BrowseAdd -> state.onBrowseAdd(event.name, event.interfaceIndex)
                    is Event.BrowseRemove -> state.onBrowseRemove(event.name)
                    is Event.Resolved -> state.onResolved(event.name, event.host, event.port)
                    is Event.AddrInfoFound -> state.onAddrInfoFound(event.name, event.ipv4, event.isAdd)
                }
            }
        }

        private suspend fun runResolve(peerName: String, interfaceIndex: Int) {
            val ref = openResolveRef(peerName, interfaceIndex) ?: return
            try {
                pollLoop(ref)
            } finally {
                deallocate(ref, "resolve($peerName)")
            }
        }

        private suspend fun runAddrInfo(peerName: String, hostname: String) {
            val ref = openAddrInfoRef(peerName, hostname) ?: return
            try {
                pollLoop(ref)
            } finally {
                deallocate(ref, "addrInfo($peerName)")
            }
        }

        private fun openResolveRef(peerName: String, interfaceIndex: Int): Pointer? {
            val refPtr = PointerByReference()
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
                    events.trySend(Event.Resolved(peerName, host, BonjourCodec.networkOrderToHost(port)))
                }
            }
            callbackAnchors.add(callback)
            val rc = DnsSd.INSTANCE.DNSServiceResolve(
                refPtr,
                0,
                interfaceIndex,
                peerName,
                SERVICE_TYPE,
                "local.",
                callback,
                null,
            )
            if (rc != DnsSd.NO_ERROR) {
                System.err.println("WARN: DNSServiceResolve('$peerName') failed rc=$rc")
                return null
            }
            return refPtr.value
        }

        private fun openAddrInfoRef(peerName: String, hostname: String): Pointer? {
            val refPtr = PointerByReference()
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
                    val ipv4 = BonjourCodec.readIpv4(address?.pointer) ?: return
                    val isAdd = (flags and DnsSd.FLAGS_ADD) != 0
                    events.trySend(Event.AddrInfoFound(peerName, ipv4, isAdd))
                }
            }
            callbackAnchors.add(callback)
            val rc = DnsSd.INSTANCE.DNSServiceGetAddrInfo(
                refPtr,
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
            return refPtr.value
        }

        private suspend fun pollLoop(ref: Pointer) {
            val fd = DnsSd.INSTANCE.DNSServiceRefSockFD(ref)
            if (fd < 0) return
            val pollfd = Memory(Posix.POLLFD_SIZE)
            while (currentCoroutineContext().isActive) {
                pollfd.setInt(Posix.OFFSET_FD, fd)
                pollfd.setShort(Posix.OFFSET_EVENTS, Posix.POLLIN)
                pollfd.setShort(Posix.OFFSET_REVENTS, 0)
                val rc = try {
                    Posix.INSTANCE.poll(pollfd, 1, POLL_TIMEOUT_MS)
                } catch (_: Throwable) {
                    return
                }
                if (rc < 0) return
                if (rc == 0) continue
                val revents = pollfd.getShort(Posix.OFFSET_REVENTS).toInt() and 0xFFFF
                val errorMask = (Posix.POLLERR.toInt() or Posix.POLLHUP.toInt() or Posix.POLLNVAL.toInt()) and 0xFFFF
                if (revents and errorMask != 0) return
                if (revents and (Posix.POLLIN.toInt() and 0xFFFF) == 0) continue
                val processRc = try {
                    DnsSd.INSTANCE.DNSServiceProcessResult(ref)
                } catch (_: Throwable) {
                    return
                }
                if (processRc != DnsSd.NO_ERROR) return
            }
        }

        sealed class Event {
            data class OwnNameAssigned(
                val canonicalName: String,
            ) : Event()

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
                val isAdd: Boolean,
            ) : Event()
        }

        companion object {
            const val SERVICE_TYPE: String = "_tether._tcp."
            private const val POLL_TIMEOUT_MS: Int = 200

            fun start(
                deviceName: String,
                port: Int,
                discoveredDevices: MutableStateFlow<List<Device>>,
            ): Session {
                val events = Channel<Event>(Channel.UNLIMITED)
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

                val session = Session(discoveredDevices, scope, events)
                val state = BonjourState(deviceName, session)
                session.bindState(state)

                val registerRef = openRegisterRef(deviceName, port, events) { session.callbackAnchors.add(it) }

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
                session.callbackAnchors.add(browseCallback)

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

                // Top-level poll loops: each owns its ref and deallocates in finally.
                scope.launch(Dispatchers.IO) {
                    try {
                        session.pollLoop(browseRef)
                    } finally {
                        deallocate(browseRef, "browse")
                    }
                }
                registerRef?.let { ref ->
                    scope.launch(Dispatchers.IO) {
                        try {
                            session.pollLoop(ref)
                        } finally {
                            deallocate(ref, "register")
                        }
                    }
                }
                scope.launch { session.consumeEvents() }
                return session
            }

            private fun openRegisterRef(
                deviceName: String,
                port: Int,
                events: Channel<Event>,
                anchor: (Any) -> Unit,
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
                            return
                        }
                        // mDNSResponder may rename the service on conflict (e.g. "Foo" →
                        // "Foo (2)"). Surface the canonical name to the state machine so
                        // self-filtering uses the actual published name.
                        val canonical = name ?: return
                        events.trySend(Event.OwnNameAssigned(canonical))
                    }
                }
                anchor(callback)
                val rc = DnsSd.INSTANCE.DNSServiceRegister(
                    ref,
                    0,
                    0,
                    deviceName,
                    SERVICE_TYPE,
                    null,
                    null,
                    BonjourCodec.hostOrderToNetwork(port),
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
        }
    }
}
