package com.tubetoast.tether.discovery

import com.tubetoast.tether.identity.DeviceIdentityStore
import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.CoroutineScope
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
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import kotlin.coroutines.CoroutineContext

private const val SERVICE_TYPE = "_tether._tcp.local."
private val IPV4_REGEX = Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""")

internal const val REQUERY_INITIAL_INTERVAL_MS = 5_000L
private const val REQUERY_MAX_INTERVAL_MS = 60_000L
private const val INTERFACE_POLL_INTERVAL_MS = 5_000L
private val log = KydraLog.withTag(default = "MdnsDiscovery.JmDNS")

/**
 * JmDNS-based discovery for non-macOS JVM hosts (Linux, Windows).
 *
 * Binds one JmDNS instance per non-loopback, non-link-local IPv4 interface address.
 * A background poller checks for interface changes every [INTERFACE_POLL_INTERVAL_MS] ms and
 * tears down stale instances or creates new ones.
 *
 * JmDNS stops querying ~675 ms after `addServiceListener`; the next refresh is
 * at ~80% of TTL (~48 min). Re-calling `addServiceListener` with the same
 * instance skips the duplicate-add (deduped by `equals`) but unconditionally
 * re-arms `ServiceResolver`. Re-verify if JmDNS is upgraded.
 */
internal class MdnsDiscoveryJmdns(
    private val store: DiscoveredDevicesStore,
    private val requeryContext: CoroutineContext,
    private val deviceIdentityStore: DeviceIdentityStore,
    private val networkInterfaceProvider: NetworkInterfaceProvider = DefaultNetworkInterfaceProvider(),
) : DeviceDiscovery {
    private val discoveryScope = CoroutineScope(SupervisorJob() + requeryContext)

    override val discoveredDevices: StateFlow<List<Device>> = store.devices

    /** Keyed by (interface name, IPv4 address). */
    internal val instances = mutableMapOf<Pair<String, InetAddress>, JmDNS>()

    @Volatile private var deviceName: String = ""

    @Volatile private var ownPort: Int = -1

    @Volatile private var fingerprint: String = ""

    @Volatile private var started: Boolean = false

    override suspend fun start(deviceName: String, port: Int) {
        val fp = deviceIdentityStore.getOrCreate()
        startSync(deviceName, port, fp)
    }

    @Synchronized
    private fun startSync(deviceName: String, port: Int, fp: String) {
        if (started) throw IllegalStateException("MdnsDiscovery already started; call stop() first")
        this.deviceName = deviceName
        this.ownPort = port
        this.fingerprint = fp
        started = true

        for (entry in networkInterfaceProvider.bindAddresses()) {
            bringUp(entry, entry.second, deviceName, port)
        }

        discoveryScope.launch {
            var requeryInterval = REQUERY_INITIAL_INTERVAL_MS
            while (isActive) {
                delay(requeryInterval)
                synchronized(this@MdnsDiscoveryJmdns) {
                    for (jmdns in instances.values) {
                        try {
                            jmdns.addServiceListener(SERVICE_TYPE, makeListener(jmdns))
                        } catch (_: Exception) {
                        }
                    }
                }
                requeryInterval = minOf(requeryInterval * 2, REQUERY_MAX_INTERVAL_MS)
            }
        }

        discoveryScope.launch {
            while (isActive) {
                delay(INTERFACE_POLL_INTERVAL_MS)
                diffInterfaces()
            }
        }
    }

    @Synchronized
    override fun republish(name: String) {
        if (!started) return
        deviceName = name
        for (jmdns in instances.values) {
            try {
                jmdns.unregisterAllServices()
            } catch (e: Exception) {
                log.warn { "unregisterAllServices (republish) failed — ${e.message}" }
            }
            try {
                jmdns.registerService(ServiceInfo.create(SERVICE_TYPE, name, ownPort, 0, 0, txtProps(fingerprint)))
            } catch (e: Exception) {
                log.warn { "registerService (republish) failed — ${e.message}" }
            }
        }
    }

    @Synchronized
    override fun stop() {
        started = false
        discoveryScope.cancel()
        for ((key, jmdns) in instances) {
            tearDown(key, jmdns)
        }
        instances.clear()
        ownPort = -1
        store.clear()
    }

    private fun bringUp(key: Pair<String, InetAddress>, addr: InetAddress, name: String, port: Int) {
        try {
            val jmdns = JmDNS.create(addr, name)
            val listener = makeListener(jmdns)
            jmdns.addServiceListener(SERVICE_TYPE, listener)
            try {
                jmdns.registerService(ServiceInfo.create(SERVICE_TYPE, name, port, 0, 0, txtProps(fingerprint)))
            } catch (e: Exception) {
                log.warn { "registerService failed on ${addr.hostAddress} — ${e.message}" }
            }
            instances[key] = jmdns
            log.info { "JmDNS bound to ${addr.hostAddress} (${key.first})" }
        } catch (e: Exception) {
            log.warn { "JmDNS create failed on ${addr.hostAddress} — ${e.message}" }
        }
    }

    private fun tearDown(key: Pair<String, InetAddress>, jmdns: JmDNS) {
        try {
            jmdns.unregisterAllServices()
        } catch (e: Exception) {
            log.warn { "unregisterAllServices failed on ${key.second.hostAddress} — ${e.message}" }
        }
        try {
            jmdns.close()
        } catch (e: Exception) {
            log.warn { "JmDNS close failed on ${key.second.hostAddress} — ${e.message}" }
        }
        log.info { "JmDNS torn down from ${key.second.hostAddress} (${key.first})" }
    }

    @Synchronized
    internal fun diffInterfaces() {
        val current = networkInterfaceProvider.bindAddresses().toSet()
        val existing = instances.keys.toSet()
        val added = current - existing
        val removed = existing - current
        for (key in removed) {
            instances.remove(key)?.let { tearDown(key, it) }
        }
        for (key in added) {
            bringUp(key, key.second, deviceName, ownPort)
        }
    }

    private fun makeListener(owningJmdns: JmDNS): ServiceListener = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {
            owningJmdns.requestServiceInfo(event.type, event.name)
        }

        override fun serviceRemoved(event: ServiceEvent) {
            log.info { "serviceRemoved '${event.name}'" }
            store.removeByName(event.name)
        }

        override fun serviceResolved(event: ServiceEvent) {
            if (!started) return
            try {
                val info: ServiceInfo = event.info
                if (info.port !in 1..65535) {
                    log.warn { "invalid port ${info.port} for '${event.name}', skipping" }
                    return
                }
                // Self-suppression by name first: TXT records (carrying the fingerprint) arrive in a
                // separate mDNS transaction from A-records, so the early serviceResolved callbacks often
                // expose a null peer fingerprint while the announce is unambiguously ours.
                if (event.name == deviceName) return
                val peerFingerprint = info.getPropertyString("fp")
                if (peerFingerprint != null && peerFingerprint == fingerprint) return
                val ipv4 = resolveIPv4(info, event.name) ?: return
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

    /** JmDNS resolves A/AAAA in stages; first callback may carry only IPv6. Returns `null` to wait for next event. */
    private fun resolveIPv4(info: ServiceInfo, serviceName: String): String? {
        val ipv4 = info.getHostAddresses().firstOrNull { IPV4_REGEX.matches(it) }
        if (ipv4 == null) {
            log.debug { "serviceResolved — no IPv4 yet for '$serviceName', skipping" }
        }
        return ipv4
    }
}
