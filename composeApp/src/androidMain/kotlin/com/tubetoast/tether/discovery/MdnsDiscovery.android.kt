package com.tubetoast.tether.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentLinkedQueue

private const val SERVICE_TYPE = "_tether._tcp."
private const val TAG = "MdnsDiscovery"

actual class MdnsDiscovery(
    private val context: Context,
    private val store: DiscoveredDevicesStore,
) : DeviceDiscovery {
    actual override val discoveredDevices: StateFlow<List<Device>> = store.devices

    @Volatile private var nsdManager: NsdManager? = null

    @Volatile private var ownName: String? = null

    @Volatile private var currentPort: Int = 0
    private var resolving = false
    private val resolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()

    @Volatile private var registrationListener: NsdManager.RegistrationListener? = null

    /**
     * NsdManager throws `IllegalArgumentException("listener already in use")` if the same
     * `RegistrationListener` instance is passed to `registerService` before its async
     * `onServiceUnregistered` callback fires. A fresh instance per registration sidesteps this.
     */
    private fun makeRegistrationListener(): NsdManager.RegistrationListener =
        object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD registration failed, errorCode=$errorCode")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD unregistration failed, errorCode=$errorCode")
            }

            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                if (nsdManager == null) return
                if (this !== registrationListener) return
                Log.d(TAG, "NSD service registered: ${serviceInfo.serviceName}")
                // NsdManager may modify the requested name (e.g. strip special chars or resolve conflicts).
                // Track the actual registered name so self-filter in onServiceFound stays accurate.
                ownName = serviceInfo.serviceName
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "NSD service unregistered: ${serviceInfo.serviceName}")
            }
        }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG, "NSD discovery start failed, errorCode=$errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG, "NSD discovery stop failed, errorCode=$errorCode")
        }

        override fun onDiscoveryStarted(serviceType: String) {
            Log.d(TAG, "NSD discovery started for $serviceType")
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.d(TAG, "NSD discovery stopped for $serviceType")
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            if (nsdManager == null) return
            if (serviceInfo.serviceName == ownName) return
            Log.d(TAG, "NSD service found: ${serviceInfo.serviceName}")
            resolveQueue.add(serviceInfo)
            startNextResolve()
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            if (nsdManager == null) return
            Log.d(TAG, "NSD service lost: ${serviceInfo.serviceName}")
            // NsdManager only populates serviceName here; host/port are null/0 — can't rebuild id.
            store.removeByName(serviceInfo.serviceName)
        }
    }

    private fun makeResolveListener() = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.w(TAG, "NSD resolve failed for '${serviceInfo.serviceName}', errorCode=$errorCode")
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
                Log.w(TAG, "NSD resolved '${serviceInfo.serviceName}' but host is null, skipping")
                onResolveComplete()
                return
            }
            val port = serviceInfo.port
            if (port !in 1..65535) {
                Log.w(TAG, "NSD invalid port $port for '${serviceInfo.serviceName}', skipping")
                onResolveComplete()
                return
            }
            val device = Device(
                name = serviceInfo.serviceName,
                host = host,
                port = port,
            )
            Log.d(TAG, "NSD peer discovered: $device")
            store.upsert(device)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            nm.resolveService(next, Runnable::run, makeResolveListener())
        } else {
            @Suppress("DEPRECATION")
            nm.resolveService(next, makeResolveListener())
        }
    }

    @Synchronized
    actual override fun start(deviceName: String, port: Int) {
        if (nsdManager != null) throw IllegalStateException("MdnsDiscovery already started; call stop() first")
        ownName = deviceName
        currentPort = port
        resolveQueue.clear()
        resolving = false
        val nm = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        nsdManager = nm
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = deviceName
            serviceType = SERVICE_TYPE
            this.port = port
        }
        Log.d(TAG, "Starting NSD: name=$deviceName, port=$port")
        makeRegistrationListener().also { fresh ->
            registrationListener = fresh
            nm.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, fresh)
        }
        nm.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    @Synchronized
    actual override fun republish(name: String) {
        val nm = nsdManager ?: return
        Log.d(TAG, "Republishing NSD as name=$name")
        val old = registrationListener
        if (old != null) {
            try {
                nm.unregisterService(old)
            } catch (e: Exception) {
                Log.w(TAG, "NSD unregisterService (republish) failed: ${e.message}")
            }
        }
        ownName = name
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = name
            serviceType = SERVICE_TYPE
            this.port = currentPort
        }
        makeRegistrationListener().also { fresh ->
            registrationListener = fresh
            nm.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, fresh)
        }
    }

    @Synchronized
    actual override fun stop() {
        val nm = nsdManager ?: return
        Log.d(TAG, "Stopping NSD")
        val listener = registrationListener
        registrationListener = null
        if (listener != null) {
            try {
                nm.unregisterService(listener)
            } catch (e: Exception) {
                Log.w(TAG, "NSD unregisterService failed: ${e.message}")
            }
        }
        try {
            nm.stopServiceDiscovery(discoveryListener)
        } catch (e: Exception) {
            Log.w(TAG, "NSD stopServiceDiscovery failed: ${e.message}")
        }
        nsdManager = null
        ownName = null
        currentPort = 0
        resolveQueue.clear()
        resolving = false
        store.clear()
    }
}
