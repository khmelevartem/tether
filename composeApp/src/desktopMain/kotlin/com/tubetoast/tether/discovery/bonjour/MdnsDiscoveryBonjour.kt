package com.tubetoast.tether.discovery.bonjour

import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import com.tubetoast.tether.discovery.DiscoveredDevicesStore
import com.tubetoast.tether.discovery.OwnNameDiscovery
import com.tubetoast.tether.discovery.txtProps
import com.tubetoast.tether.identity.DeviceIdentityStore
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.warn
import ru.pocketbyte.kydra.log.wrapper.withTag
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

private val log = KydraLog.withTag(default = "MdnsDiscovery.Bonjour")

// ownPublishedName is set by the DNSServiceRegister callback, which requires a live mDNSResponder.
// This path cannot be unit-tested on Linux/Windows CI where mDNSResponder is absent.

/**
 * JVM-on-macOS mDNS discovery backed by Apple's DNS-SD API ([DnsSd]).
 *
 * On macOS the kernel routes WiFi mDNS multicast exclusively to mDNSResponder;
 * user-space sockets (JmDNS) never see external peers. Going through DNS-SD
 * makes us a peer of mDNSResponder. See `docs/knowledge/macos-mdns-bonjour.md`.
 *
 * Each `DNSServiceRef` is owned by one polling coroutine that calls
 * `DNSServiceRefDeallocate` in its own `finally` block — the pattern required
 * by `dns_sd.h` to avoid a concurrent-deallocate race.
 */
internal class MdnsDiscoveryBonjour(
    private val store: DiscoveredDevicesStore,
    private val deviceIdentityStore: DeviceIdentityStore,
) : OwnNameDiscovery {
    override val discoveredDevices: StateFlow<List<Device>> = store.devices

    private val _ownPublishedName = MutableStateFlow<String?>(null)
    override val ownPublishedName: StateFlow<String?> = _ownPublishedName

    private val lifecycleLock = Mutex()

    @Volatile private var session: Session? = null

    @Volatile private var currentPort: Int = 0

    @Volatile private var stopped: Boolean = false

    @Volatile private var fingerprint: String = ""

    override suspend fun start(deviceName: String, port: Int) {
        val fingerprint = deviceIdentityStore.getOrCreate()
        lifecycleLock.withLock {
            if (session != null) throw IllegalStateException("MdnsDiscovery already started; call stop() first")
            stopped = false
            currentPort = port
            this.fingerprint = fingerprint
            _ownPublishedName.value = null
            session = Session.start(deviceName, port, store, fingerprint) { _ownPublishedName.value = it }
        }
    }

    override suspend fun stop() {
        val toClose = lifecycleLock.withLock {
            stopped = true
            val current = session
            session = null
            currentPort = 0
            current
        }
        toClose?.close()
        _ownPublishedName.value = null
        store.clear()
    }

    override suspend fun republish(name: String) {
        val (toClose, port) = lifecycleLock.withLock {
            val current = session ?: return
            val port = currentPort
            session = null
            current to port
        }
        toClose.close()
        lifecycleLock.withLock {
            if (stopped) return
            if (session == null) {
                _ownPublishedName.value = null
                session = Session.start(name, port, store, fingerprint) { _ownPublishedName.value = it }
            }
        }
    }

    private class Session(
        private val store: DiscoveredDevicesStore,
        private val scope: CoroutineScope,
        private val events: Channel<Event>,
    ) : BonjourState.Sink {
        private lateinit var state: BonjourState

        /**
         * Per-name jobs. Cancelling a job stops its poll loop and triggers
         * self-deallocation of the corresponding `DNSServiceRef` in the loop's
         * `finally` block — the canonical pattern that satisfies the
         * `dns_sd.h` same-thread invariant.
         */
        private val resolveJobs = ConcurrentHashMap<String, Job>()
        private val addrInfoJobs = ConcurrentHashMap<String, Job>()

        /**
         * Anchors JNA callbacks against GC for the session lifetime. Without
         * strong references, JNA's `CallbackReference` can free the trampoline
         * while mDNSResponder still holds a pointer to it.
         */
        val callbackAnchors = CopyOnWriteArrayList<Any>()

        /** Blocks up to [POLL_TIMEOUT_MS]; UI callers should use a background dispatcher. */
        fun close() {
            events.close()
            runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
            resolveJobs.clear()
            addrInfoJobs.clear()
            callbackAnchors.clear()
        }

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

        private fun bindState(state: BonjourState) {
            this.state = state
        }

        private suspend fun consumeEvents() {
            for (event in events) {
                when (event) {
                    is Event.BrowseAdd -> state.onBrowseAdd(event.name, event.interfaceIndex)
                    is Event.BrowseRemove -> state.onBrowseRemove(event.name)
                    is Event.Resolved -> state.onResolved(event.name, event.host, event.port, event.peerFingerprint)
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
            val outRef = PointerByReference()
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
                    val peerFingerprint = if (txtRecord != null && txtLen > 0) {
                        val bytes = txtRecord.getByteArray(0, txtLen.toInt() and 0xFFFF)
                        BonjourCodec.decodeTxt(bytes)["fp"]
                    } else {
                        null
                    }
                    events.trySend(
                        Event.Resolved(peerName, host, BonjourCodec.networkOrderToHost(port), peerFingerprint),
                    )
                }
            }
            callbackAnchors.add(callback)
            val error = DnsSd.INSTANCE.DNSServiceResolve(
                outRef,
                0,
                interfaceIndex,
                peerName,
                SERVICE_TYPE,
                "local.",
                callback,
                null,
            )
            if (error != DnsSd.NO_ERROR) {
                log.warn { "DNSServiceResolve('$peerName') failed error=$error" }
                return null
            }
            return outRef.value
        }

        private fun openAddrInfoRef(peerName: String, hostname: String): Pointer? {
            val outRef = PointerByReference()
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
            val error = DnsSd.INSTANCE.DNSServiceGetAddrInfo(
                outRef,
                0,
                0,
                DnsSd.PROTOCOL_IPV4,
                hostname,
                callback,
                null,
            )
            if (error != DnsSd.NO_ERROR) {
                log.warn { "DNSServiceGetAddrInfo('$hostname') failed error=$error" }
                return null
            }
            return outRef.value
        }

        private suspend fun pollLoop(ref: Pointer) {
            val socketFd = DnsSd.INSTANCE.DNSServiceRefSockFD(ref)
            if (socketFd < 0) return
            val pollfd = Memory(Posix.POLLFD_SIZE)
            while (currentCoroutineContext().isActive) {
                pollfd.setInt(Posix.OFFSET_FD, socketFd)
                pollfd.setShort(Posix.OFFSET_EVENTS, Posix.POLLIN)
                pollfd.setShort(Posix.OFFSET_REVENTS, 0)
                val pollResult = try {
                    Posix.INSTANCE.poll(pollfd, 1, POLL_TIMEOUT_MS)
                } catch (_: Throwable) {
                    return
                }
                if (pollResult < 0) return
                if (pollResult == 0) continue
                val revents = pollfd.getShort(Posix.OFFSET_REVENTS).toInt() and 0xFFFF
                val errorMask = (Posix.POLLERR.toInt() or Posix.POLLHUP.toInt() or Posix.POLLNVAL.toInt()) and 0xFFFF
                if (revents and errorMask != 0) return
                if (revents and (Posix.POLLIN.toInt() and 0xFFFF) == 0) continue
                val processError = try {
                    DnsSd.INSTANCE.DNSServiceProcessResult(ref)
                } catch (_: Throwable) {
                    return
                }
                if (processError != DnsSd.NO_ERROR) return
            }
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
                val peerFingerprint: String?,
            ) : Event()

            data class AddrInfoFound(
                val name: String,
                val ipv4: String,
                val isAdd: Boolean,
            ) : Event()
        }

        companion object {
            const val SERVICE_TYPE: String = "_tether._tcp."
            const val POLL_TIMEOUT_MS: Int = 200

            fun start(
                deviceName: String,
                port: Int,
                store: DiscoveredDevicesStore,
                fingerprint: String = "",
                onNameAssigned: (String) -> Unit = {},
            ): Session {
                val events = Channel<Event>(Channel.UNLIMITED)
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

                val session = Session(store, scope, events)
                val state = BonjourState(store, session, fingerprint) { host, resolvedPort ->
                    resolvedPort == port && localAddresses().contains(host)
                }
                session.bindState(state)

                // interfaceIndex=0 → kDNSServiceInterfaceIndexAny; Bonjour fans out to all interfaces by default.
                val registerRef = openRegisterRef(deviceName, port, fingerprint, onNameAssigned) { session.callbackAnchors.add(it) }

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

                val browseOutRef = PointerByReference()
                // interfaceIndex=0 → kDNSServiceInterfaceIndexAny; Bonjour fans out to all interfaces by default.
                val error = DnsSd.INSTANCE.DNSServiceBrowse(
                    browseOutRef,
                    0,
                    0,
                    SERVICE_TYPE,
                    null,
                    browseCallback,
                    null,
                )
                if (error != DnsSd.NO_ERROR) {
                    registerRef?.let { deallocate(it, "register(rollback)") }
                    scope.cancel()
                    events.close()
                    throw IllegalStateException("DNSServiceBrowse failed error=$error")
                }
                val browseRef = browseOutRef.value
                    ?: throw IllegalStateException("DNSServiceBrowse returned null sdRef")

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
                fingerprint: String,
                onNameAssigned: (String) -> Unit,
                anchor: (Any) -> Unit,
            ): Pointer? {
                val outRef = PointerByReference()
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
                            log.warn { "DNSServiceRegister callback errorCode=$errorCode" }
                            return
                        }
                        if (name != null) {
                            log.info { "DNSServiceRegister assigned name='$name'" }
                            onNameAssigned(name)
                        }
                    }
                }
                anchor(callback)
                val txtBytes = BonjourCodec.encodeTxt(txtProps(fingerprint))
                val txtMem = Memory(txtBytes.size.toLong()).also { it.write(0, txtBytes, 0, txtBytes.size) }
                val error = DnsSd.INSTANCE.DNSServiceRegister(
                    outRef,
                    0,
                    0,
                    deviceName,
                    SERVICE_TYPE,
                    null,
                    null,
                    BonjourCodec.hostOrderToNetwork(port),
                    txtBytes.size.toShort(),
                    txtMem,
                    callback,
                    null,
                )
                if (error != DnsSd.NO_ERROR) {
                    log.warn { "DNSServiceRegister failed error=$error; running browse-only" }
                    return null
                }
                return outRef.value
            }

            private fun deallocate(ref: Pointer, label: String) {
                try {
                    DnsSd.INSTANCE.DNSServiceRefDeallocate(ref)
                } catch (e: Throwable) {
                    log.warn { "Bonjour $label deallocate failed — ${e.message}" }
                }
            }

            private fun localAddresses(): Set<String> =
                NetworkInterface
                    .getNetworkInterfaces()
                    ?.asSequence()
                    ?.flatMap { it.inetAddresses.asSequence() }
                    ?.map { it.hostAddress }
                    ?.toSet()
                    ?: emptySet()
        }
    }
}
