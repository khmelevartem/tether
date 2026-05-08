package com.tubetoast.tether.discovery

import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

private const val SERVICE_TYPE = "_tether._tcp.local."
private val IPV4_REGEX = Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""")

// JmDNS's ServiceResolver sends 3 PTR queries within ~675ms of addServiceListener and then
// stops. After that JmDNS only re-queries when cached records become stale (~80% of TTL,
// i.e. ~48 minutes by default). If a peer's initial multicast announcement is missed
// (transient packet loss, WiFi power-save, etc.), discovery stalls until the next
// announcement from the peer — observed in production as 2–3 minute latency for Android
// peers (issue #47). Re-invoking addServiceListener with the same listener instance does
// not add a duplicate (deduped via equals) but unconditionally re-arms ServiceResolver,
// flushing out late or lost announcements.
//
// Verified against JmDNS 3.5.9 (libs.versions.toml): JmDNSImpl.addServiceListener checks
// list.contains(status) via ListenerStatus.equals → getListener().equals() before adding,
// and calls startServiceResolver(type) unconditionally afterwards regardless of dedup.
// If JmDNS is upgraded, re-verify this invariant in JmDNSImpl.addServiceListener.
private const val REQUERY_INITIAL_INTERVAL_MS = 5_000L
private const val REQUERY_MAX_INTERVAL_MS = 60_000L

actual class MdnsDiscovery {
    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    actual val discoveredDevices: StateFlow<List<Device>> = _discoveredDevices.asStateFlow()

    @Volatile private var jmdns: JmDNS? = null

    // Self-filter: identify own service by the IP JmDNS bound to and the registered port.
    // Name-based filtering is unreliable — JmDNS may rename the service on conflict
    // (e.g. "Foo" → "Foo (2)"), and stale records of the old name linger after a restart.
    @Volatile private var ownIp: String? = null

    @Volatile private var ownPort: Int = -1

    @Volatile private var serviceListener: ServiceListener? = null
    private var requeryScope: CoroutineScope? = null

    @Synchronized
    actual fun start(deviceName: String, port: Int) {
        if (jmdns != null) throw IllegalStateException("MdnsDiscovery already started; call stop() first")

        val instance = try {
            JmDNS.create()
        } catch (e: Exception) {
            throw e
        }
        jmdns = instance
        ownIp = instance.inetAddress.hostAddress
        ownPort = port

        val listener = object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent) {
                jmdns?.requestServiceInfo(event.type, event.name)
            }

            override fun serviceRemoved(event: ServiceEvent) {
                System.err.println("INFO: serviceRemoved '${event.name}'")
                _discoveredDevices.value = _discoveredDevices.value
                    .filterNot { it.name == event.name }
            }

            override fun serviceResolved(event: ServiceEvent) {
                if (jmdns == null) return
                try {
                    val info: ServiceInfo = event.info

                    if (info.port !in 1..65535) {
                        System.err.println("WARN: invalid port ${info.port} for '${event.name}', skipping")
                        return
                    }

                    val ipv4 = info.getHostAddresses().firstOrNull { IPV4_REGEX.matches(it) }
                    if (ipv4 == null) {
                        // JmDNS resolves A/AAAA records in stages — first callback can carry only
                        // IPv6, IPv4 arrives on a later callback. Skip quietly and wait for the
                        // next event. A persistent IPv6-only state across the peer's lifetime
                        // is a separate concern (see follow-up logger issue).
                        System.err.println(
                            "DEBUG: serviceResolved — no IPv4 yet for '${event.name}', skipping",
                        )
                        return
                    }

                    // Filter self: same IP and same port uniquely identifies our own service,
                    // regardless of the name JmDNS assigned (which may differ from deviceName).
                    if (ipv4 == ownIp && info.port == ownPort) return

                    val device = Device(
                        id = "${event.name}@$ipv4:${info.port}",
                        name = event.name,
                        host = ipv4,
                        port = info.port,
                    )
                    _discoveredDevices.value = _discoveredDevices.value
                        .filterNot { it.name == device.name } + device
                } catch (e: Exception) {
                    System.err.println("WARN: serviceResolved error for '${event.name}' — ${e.message}")
                }
            }
        }
        serviceListener = listener
        instance.addServiceListener(SERVICE_TYPE, listener)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        requeryScope = scope
        scope.launch {
            var interval = REQUERY_INITIAL_INTERVAL_MS
            while (isActive) {
                delay(interval)
                val jm = jmdns ?: break
                val sl = serviceListener ?: break
                try {
                    jm.addServiceListener(SERVICE_TYPE, sl)
                } catch (e: Exception) {
                    // JmDNS may be mid-close; nothing to do.
                }
                interval = minOf(interval * 2, REQUERY_MAX_INTERVAL_MS)
            }
        }

        try {
            instance.registerService(
                ServiceInfo.create(SERVICE_TYPE, deviceName, port, ""),
            )
        } catch (e: Exception) {
            scope.cancel()
            requeryScope = null
            serviceListener = null
            jmdns = null
            ownIp = null
            ownPort = -1
            try {
                instance.close()
            } catch (ignored: Exception) {
            }
            throw e
        }
    }

    @Synchronized
    actual fun stop() {
        requeryScope?.cancel()
        requeryScope = null
        serviceListener = null
        try {
            try {
                jmdns?.unregisterAllServices()
            } catch (e: Exception) {
                System.err.println("WARN: unregisterAllServices failed — ${e.message}")
            }
            try {
                jmdns?.close()
            } catch (e: Exception) {
                System.err.println("WARN: jmdns close failed — ${e.message}")
            }
        } finally {
            jmdns = null
            ownIp = null
            ownPort = -1
            _discoveredDevices.value = emptyList()
        }
    }
}
