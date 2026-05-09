package com.tubetoast.tether.discovery

import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
import kotlin.coroutines.CoroutineContext

private const val SERVICE_TYPE = "_tether._tcp.local."
private val IPV4_REGEX = Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""")

// JmDNS-based discovery for non-macOS JVM hosts (Linux, Windows). On those platforms
// no system-wide mDNSResponder daemon is running, so a raw multicast socket on
// 224.0.0.251:5353 receives announcements directly from peers.
//
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
internal const val REQUERY_INITIAL_INTERVAL_MS = 5_000L
private const val REQUERY_MAX_INTERVAL_MS = 60_000L

internal class MdnsDiscoveryJmdns {
    private val requeryContext: CoroutineContext

    constructor() {
        requeryContext = Dispatchers.IO
        requeryScope = CoroutineScope(SupervisorJob() + requeryContext)
    }

    // Internal — for tests: inject a TestDispatcher to control re-query timing via advanceTimeBy().
    internal constructor(testContext: CoroutineContext) {
        requeryContext = testContext
        requeryScope = CoroutineScope(SupervisorJob() + requeryContext)
    }

    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    val discoveredDevices: StateFlow<List<Device>> = _discoveredDevices.asStateFlow()

    @Volatile private var jmdns: JmDNS? = null

    // IP+port, not name: JmDNS may rename a service on conflict (e.g. "Foo" → "Foo (2)").
    @Volatile private var ownIp: String? = null

    @Volatile private var ownPort: Int = -1

    private lateinit var requeryScope: CoroutineScope
    private var requeryJob: Job? = null

    @Synchronized
    fun start(deviceName: String, port: Int) {
        if (jmdns != null) throw IllegalStateException("MdnsDiscovery already started; call stop() first")

        val instance = JmDNS.create()
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
                    val ipv4 = resolveIPv4(info, event.name) ?: return
                    if (isSelf(ipv4, info.port)) return
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
        instance.addServiceListener(SERVICE_TYPE, listener)

        requeryJob = requeryScope.launch {
            var interval = REQUERY_INITIAL_INTERVAL_MS
            while (isActive) {
                delay(interval)
                val jm = jmdns ?: break
                try {
                    jm.addServiceListener(SERVICE_TYPE, listener)
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
            requeryJob?.cancel()
            requeryJob = null
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
    fun stop() {
        requeryJob?.cancel()
        requeryJob = null
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

    // JmDNS may deliver only IPv6 on the first callback; IPv4 arrives later.
    private fun resolveIPv4(info: ServiceInfo, serviceName: String): String? {
        val ipv4 = info.getHostAddresses().firstOrNull { IPV4_REGEX.matches(it) }
        if (ipv4 == null) {
            System.err.println("DEBUG: serviceResolved — no IPv4 yet for '$serviceName', skipping")
        }
        return ipv4
    }

    private fun isSelf(ipv4: String, port: Int): Boolean = ipv4 == ownIp && port == ownPort
}
