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

actual class MdnsDiscovery actual constructor() {
    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    actual val discoveredDevices: Flow<List<Device>> = _discoveredDevices.asStateFlow()

    private var jmdns: JmDNS? = null
    private var ownName: String? = null

    @Synchronized
    actual fun start(deviceName: String, port: Int) {
        if (jmdns != null) throw IllegalStateException("MdnsDiscovery already started; call stop() first")

        ownName = deviceName
        val instance = JmDNS.create()
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
                    try {
                        if (event.name == ownName) return

                        val info: ServiceInfo = event.info
                        val ipv4 = info.getHostAddresses().firstOrNull { it.contains('.') }
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

        instance.registerService(
            ServiceInfo.create(
                SERVICE_TYPE,
                deviceName,
                port,
                "",
            ),
        )
    }

    @Synchronized
    actual fun stop() {
        try {
            jmdns?.unregisterAllServices()
            jmdns?.close()
        } finally {
            jmdns = null
            ownName = null
            _discoveredDevices.value = emptyList()
        }
    }
}
