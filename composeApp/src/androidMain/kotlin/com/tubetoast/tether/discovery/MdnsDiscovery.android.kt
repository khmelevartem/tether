package com.tubetoast.tether.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.tubetoast.tether.identity.DeviceIdentityStore
import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.debug
import ru.pocketbyte.kydra.log.warn
import ru.pocketbyte.kydra.log.wrapper.withTag
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

private const val SERVICE_TYPE = "_tether._tcp."
private val log = KydraLog.withTag(default = "MdnsDiscovery.Android")

actual class MdnsDiscovery(
    private val context: Context,
    private val store: DiscoveredDevicesStore,
    private val deviceIdentityStore: DeviceIdentityStore,
) : DeviceDiscovery {
    actual override val discoveredDevices: StateFlow<List<Device>> = store.devices

    private val lifecycleLock = Mutex()

    @Volatile private var fingerprint: String = ""

    @Volatile private var nsdManager: NsdManager? = null

    @Volatile private var ownName: String? = null

    @Volatile private var currentPort: Int = 0
    private val resolving = AtomicBoolean(false)
    private val resolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()
    private val nameToFingerprint = mutableMapOf<String, String>()

    @Volatile private var registrationListener: NsdManager.RegistrationListener? = null

    /**
     * NsdManager throws `IllegalArgumentException("listener already in use")` if the same
     * `RegistrationListener` instance is passed to `registerService` before its async
     * `onServiceUnregistered` callback fires. A fresh instance per registration sidesteps this.
     */
    private fun makeRegistrationListener(): NsdManager.RegistrationListener =
        object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                log.warn { "NSD registration failed, errorCode=$errorCode" }
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                log.warn { "NSD unregistration failed, errorCode=$errorCode" }
            }

            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                if (nsdManager == null) return
                if (this !== registrationListener) return
                log.debug { "NSD service registered: ${serviceInfo.serviceName}" }
                // NsdManager may modify the requested name (e.g. strip special chars or resolve conflicts).
                // Track the actual registered name so self-filter in onServiceFound stays accurate.
                ownName = serviceInfo.serviceName
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                log.debug { "NSD service unregistered: ${serviceInfo.serviceName}" }
            }
        }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            log.warn { "NSD discovery start failed, errorCode=$errorCode" }
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            log.warn { "NSD discovery stop failed, errorCode=$errorCode" }
        }

        override fun onDiscoveryStarted(serviceType: String) {
            log.debug { "NSD discovery started for $serviceType" }
        }

        override fun onDiscoveryStopped(serviceType: String) {
            log.debug { "NSD discovery stopped for $serviceType" }
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            if (nsdManager == null) return
            if (serviceInfo.serviceName == ownName) return
            log.debug { "NSD service found: ${serviceInfo.serviceName}" }
            resolveQueue.add(serviceInfo)
            startNextResolve()
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            if (nsdManager == null) return
            log.debug { "NSD service lost: ${serviceInfo.serviceName}" }
            nameToFingerprint.remove(serviceInfo.serviceName)?.let(store::removeByFingerprint)
        }
    }

    private fun makeResolveListener() = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            log.warn { "NSD resolve failed for '${serviceInfo.serviceName}', errorCode=$errorCode" }
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
                log.warn { "NSD resolved '${serviceInfo.serviceName}' but host is null, skipping" }
                onResolveComplete()
                return
            }
            val port = serviceInfo.port
            if (port !in 1..65535) {
                log.warn { "NSD invalid port $port for '${serviceInfo.serviceName}', skipping" }
                onResolveComplete()
                return
            }
            val peerFingerprint = serviceInfo.attributes["fp"]?.decodeToString()
            if (peerFingerprint == null) {
                log.debug { "NSD resolved '${serviceInfo.serviceName}' without fp TXT, deferring upsert" }
                onResolveComplete()
                return
            }
            nameToFingerprint[serviceInfo.serviceName] = peerFingerprint
            val device = Device(
                name = serviceInfo.serviceName,
                host = host,
                port = port,
                fingerprint = peerFingerprint,
            )
            log.debug { "NSD peer discovered: $device" }
            store.upsert(device)
            onResolveComplete()
        }
    }

    private fun onResolveComplete() {
        resolving.set(false)
        startNextResolve()
    }

    private fun startNextResolve() {
        if (!resolving.compareAndSet(false, true)) return
        val nm = nsdManager ?: run {
            resolving.set(false)
            return
        }
        val next = resolveQueue.poll() ?: run {
            resolving.set(false)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            nm.resolveService(next, Runnable::run, makeResolveListener())
        } else {
            @Suppress("DEPRECATION")
            nm.resolveService(next, makeResolveListener())
        }
    }

    actual override suspend fun start(deviceName: String, port: Int) {
        val fingerprint = deviceIdentityStore.getOrCreate()
        lifecycleLock.withLock {
            if (nsdManager != null) throw IllegalStateException("MdnsDiscovery already started; call stop() first")
            this.fingerprint = fingerprint
            ownName = deviceName
            currentPort = port
            resolveQueue.clear()
            resolving.set(false)
            val nm = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            nsdManager = nm
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = deviceName
                serviceType = SERVICE_TYPE
                this.port = port
                txtProps(fingerprint).forEach { (k, v) -> setAttribute(k, v) }
            }
            log.debug { "Starting NSD: name=$deviceName, port=$port" }
            makeRegistrationListener().also { fresh ->
                registrationListener = fresh
                nm.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, fresh)
            }
            nm.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }
    }

    private fun unregisterPreviousListener(nm: NsdManager, label: String) {
        val old = registrationListener
        if (old != null) {
            try {
                nm.unregisterService(old)
            } catch (e: Exception) {
                log.warn { "NSD unregisterService ($label) failed: ${e.message}" }
            }
        }
    }

    actual override suspend fun republish(name: String) {
        lifecycleLock.withLock {
            val nm = nsdManager ?: return
            log.debug { "Republishing NSD as name=$name" }
            unregisterPreviousListener(nm, "republish")
            ownName = name
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = name
                serviceType = SERVICE_TYPE
                this.port = currentPort
                txtProps(fingerprint).forEach { (k, v) -> setAttribute(k, v) }
            }
            makeRegistrationListener().also { fresh ->
                registrationListener = fresh
                nm.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, fresh)
            }
        }
    }

    actual override suspend fun stop() {
        lifecycleLock.withLock {
            val nm = nsdManager ?: return
            log.debug { "Stopping NSD" }
            unregisterPreviousListener(nm, "stop")
            registrationListener = null
            try {
                nm.stopServiceDiscovery(discoveryListener)
            } catch (e: Exception) {
                log.warn { "NSD stopServiceDiscovery failed: ${e.message}" }
            }
            nsdManager = null
            ownName = null
            currentPort = 0
            resolveQueue.clear()
            resolving.set(false)
            nameToFingerprint.clear()
            store.clear()
        }
    }
}
