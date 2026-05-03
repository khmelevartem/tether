package com.tubetoast.tether.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.tubetoast.tether.TetherApp
import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue

private const val SERVICE_TYPE = "_tether._tcp."

actual class MdnsDiscovery actual constructor() {
    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    actual val discoveredDevices: Flow<List<Device>> = _discoveredDevices.asStateFlow()

    @Volatile private var nsdManager: NsdManager? = null

    @Volatile private var ownName: String? = null
    private var resolving = false
    private val resolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            System.err.println("WARN: NSD registration failed, errorCode=$errorCode")
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            System.err.println("WARN: NSD unregistration failed, errorCode=$errorCode")
        }

        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}

        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            System.err.println("WARN: NSD discovery start failed, errorCode=$errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            System.err.println("WARN: NSD discovery stop failed, errorCode=$errorCode")
        }

        override fun onDiscoveryStarted(serviceType: String) {}

        override fun onDiscoveryStopped(serviceType: String) {}

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            if (nsdManager == null) return
            if (serviceInfo.serviceName == ownName) return
            resolveQueue.add(serviceInfo)
            startNextResolve()
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            _discoveredDevices.value = _discoveredDevices.value
                .filterNot { it.name == serviceInfo.serviceName }
        }
    }

    private fun makeResolveListener() = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            System.err.println("WARN: NSD resolve failed for '${serviceInfo.serviceName}', errorCode=$errorCode")
            onResolveComplete()
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            if (nsdManager == null) {
                onResolveComplete()
                return
            }
            val inetAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                serviceInfo.hostAddresses.firstOrNull()
            } else {
                @Suppress("DEPRECATION")
                serviceInfo.host
            }
            val host = inetAddress?.hostAddress
            if (host == null) {
                System.err.println("WARN: NSD resolved '${serviceInfo.serviceName}' but host is null, skipping")
                onResolveComplete()
                return
            }
            val port = serviceInfo.port
            if (port !in 1..65535) {
                System.err.println("WARN: invalid port $port for '${serviceInfo.serviceName}', skipping")
                onResolveComplete()
                return
            }
            val device = Device(
                id = "${serviceInfo.serviceName}@$host:$port",
                name = serviceInfo.serviceName,
                host = host,
                port = port,
            )
            _discoveredDevices.value = _discoveredDevices.value
                .filterNot { it.name == device.name } + device
            onResolveComplete()
        }
    }

    @Synchronized
    private fun onResolveComplete() {
        resolving = false
        startNextResolve()
    }

    @Synchronized
    private fun startNextResolve() {
        if (resolving) return
        val nm = nsdManager ?: return
        val next = resolveQueue.poll() ?: return
        resolving = true
        @Suppress("DEPRECATION")
        nm.resolveService(next, makeResolveListener())
    }

    @Synchronized
    actual fun start(deviceName: String, port: Int) {
        if (nsdManager != null) throw IllegalStateException("MdnsDiscovery already started; call stop() first")
        ownName = deviceName
        resolveQueue.clear()
        resolving = false
        val nm = TetherApp.context.getSystemService(Context.NSD_SERVICE) as NsdManager
        nsdManager = nm
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = deviceName
            serviceType = SERVICE_TYPE
            this.port = port
        }
        nm.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        nm.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    @Synchronized
    actual fun stop() {
        val nm = nsdManager ?: return
        try {
            nm.unregisterService(registrationListener)
        } catch (e: Exception) {
            System.err.println("WARN: NSD unregisterService failed — ${e.message}")
        }
        try {
            nm.stopServiceDiscovery(discoveryListener)
        } catch (e: Exception) {
            System.err.println("WARN: NSD stopServiceDiscovery failed — ${e.message}")
        }
        nsdManager = null
        ownName = null
        resolveQueue.clear()
        resolving = false
        _discoveredDevices.value = emptyList()
    }
}
