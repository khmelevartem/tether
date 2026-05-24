package com.tubetoast.tether.discovery

import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.debug
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.warn
import ru.pocketbyte.kydra.log.wrapper.withTag
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import kotlin.coroutines.CoroutineContext

private const val SERVICE_TYPE = "_tether._tcp.local."
private val IPV4_REGEX = Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""")

internal const val REQUERY_INITIAL_INTERVAL_MS = 5_000L
private const val REQUERY_MAX_INTERVAL_MS = 60_000L
private val log = KydraLog.withTag(default = "Tether.MdnsDiscovery.JmDNS")

/**
 * JmDNS-based discovery for non-macOS JVM hosts (Linux, Windows).
 *
 * JmDNS stops querying ~675 ms after `addServiceListener`; the next refresh is
 * at ~80% of TTL (~48 min). Re-calling `addServiceListener` with the same
 * instance skips the duplicate-add (deduped by `equals`) but unconditionally
 * re-arms `ServiceResolver` — re-verified in JmDNS 3.5.9 `JmDNSImpl`.
 * Re-verify if JmDNS is upgraded.
 */
internal class MdnsDiscoveryJmdns(
    private val store: DiscoveredDevicesStore,
    private val requeryContext: CoroutineContext,
) : DeviceDiscovery {
    private val requeryScope = CoroutineScope(SupervisorJob() + requeryContext)

    override val discoveredDevices: StateFlow<List<Device>> = store.devices

    @Volatile private var jmdns: JmDNS? = null

    /** IP+port, not name — JmDNS may rename on conflict (e.g. `Foo` → `Foo (2)`). */
    @Volatile private var ownIp: String? = null

    @Volatile private var ownPort: Int = -1

    @Volatile private var requeryJob: Job? = null

    @Synchronized
    override fun start(deviceName: String, port: Int) {
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
                log.info { "serviceRemoved '${event.name}'" }
                store.removeByName(event.name)
            }

            override fun serviceResolved(event: ServiceEvent) {
                if (jmdns == null) return
                try {
                    val info: ServiceInfo = event.info
                    if (info.port !in 1..65535) {
                        log.warn { "invalid port ${info.port} for '${event.name}', skipping" }
                        return
                    }
                    val ipv4 = resolveIPv4(info, event.name) ?: return
                    if (isSelf(ipv4, info.port)) return
                    val device = Device(
                        name = event.name,
                        host = ipv4,
                        port = info.port,
                    )
                    store.upsert(device)
                } catch (e: Exception) {
                    log.warn { "serviceResolved error for '${event.name}' — ${e.message}" }
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
                    // JmDNS may be mid-close.
                }
                interval = minOf(interval * 2, REQUERY_MAX_INTERVAL_MS)
            }
        }

        try {
            instance.registerService(
                ServiceInfo.create(SERVICE_TYPE, deviceName, port, 0, 0, TXT_PROPS),
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
    override fun republish(name: String) {
        val instance = jmdns ?: return
        try {
            instance.unregisterAllServices()
        } catch (e: Exception) {
            log.warn { "unregisterAllServices (republish) failed — ${e.message}" }
        }
        try {
            instance.registerService(ServiceInfo.create(SERVICE_TYPE, name, ownPort, 0, 0, TXT_PROPS))
        } catch (e: Exception) {
            log.warn { "registerService (republish) failed — ${e.message}" }
        }
    }

    @Synchronized
    override fun stop() {
        requeryJob?.cancel()
        requeryJob = null
        try {
            try {
                jmdns?.unregisterAllServices()
            } catch (e: Exception) {
                log.warn { "unregisterAllServices failed — ${e.message}" }
            }
            try {
                jmdns?.close()
            } catch (e: Exception) {
                log.warn { "jmdns close failed — ${e.message}" }
            }
        } finally {
            jmdns = null
            ownIp = null
            ownPort = -1
            store.clear()
        }
    }

    /** JmDNS resolves A/AAAA in stages; first callback may carry only IPv6. Returns `null` to wait for next event. */
    private fun resolveIPv4(info: ServiceInfo, serviceName: String): String? {
        val ipv4 = info.getHostAddresses().firstOrNull { IPV4_REGEX.matches(it) }
        if (ipv4 == null) {
            log.debug { "serviceResolved — no IPv4 yet for '$serviceName', skipping" }
        }
        return ipv4
    }

    private fun isSelf(ipv4: String, port: Int): Boolean = ipv4 == ownIp && port == ownPort
}
