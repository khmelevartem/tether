package com.tubetoast.tether.discovery

import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

private const val SERVICE_TYPE = "_tether._tcp.local."
private val IPV4_REGEX = Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""")

actual class MdnsDiscovery actual constructor() {
    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    actual val discoveredDevices: Flow<List<Device>> = _discoveredDevices.asStateFlow()

    @Volatile private var jmdns: JmDNS? = null

    @Volatile private var ownName: String? = null

    @Synchronized
    actual fun start(deviceName: String, port: Int) {
        if (jmdns != null) throw IllegalStateException("MdnsDiscovery already started; call stop() first")

        ownName = deviceName
        val instance = try {
            JmDNS.create()
        } catch (e: Exception) {
            ownName = null
            throw e
        }
        jmdns = instance

        instance.addServiceListener(
            SERVICE_TYPE,
            object : ServiceListener {
                override fun serviceAdded(event: ServiceEvent) {
                    jmdns?.requestServiceInfo(event.type, event.name)
                }

                override fun serviceRemoved(event: ServiceEvent) {
                    _discoveredDevices.value = _discoveredDevices.value
                        .filterNot { it.name == event.name }
                }

                override fun serviceResolved(event: ServiceEvent) {
                    if (jmdns == null) return
                    try {
                        if (event.name == ownName) return

                        val info: ServiceInfo = event.info

                        if (info.port !in 1..65535) {
                            System.err.println("WARN: invalid port ${info.port} for '${event.name}', skipping")
                            return
                        }

                        val ipv4 = info.getHostAddresses().firstOrNull { IPV4_REGEX.matches(it) }
                        if (ipv4 == null) {
                            System.err.println(
                                "WARN: serviceResolved — no IPv4 for '${event.name}', skipping",
                            )
                            return
                        }

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
            },
        )

        try {
            instance.registerService(
                ServiceInfo.create(
                    SERVICE_TYPE,
                    deviceName,
                    port,
                    "",
                ),
            )
        } catch (e: Exception) {
            jmdns = null
            ownName = null
            try {
                instance.close()
            } catch (ignored: Exception) {
            }
            throw e
        }
    }

    @Synchronized
    actual fun stop() {
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
            ownName = null
            _discoveredDevices.value = emptyList()
        }
    }
}
