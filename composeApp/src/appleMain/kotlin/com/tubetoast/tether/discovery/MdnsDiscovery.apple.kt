@file:OptIn(ExperimentalForeignApi::class)

package com.tubetoast.tether.discovery

import com.tubetoast.tether.identity.DeviceIdentityStore
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.util.toNSData
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSData
import platform.Foundation.NSNetService
import platform.Foundation.NSNetServiceBrowser
import platform.Foundation.NSNetServiceBrowserDelegateProtocol
import platform.Foundation.NSNetServiceDelegateProtocol
import platform.darwin.NSObject
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.debug
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.warn
import ru.pocketbyte.kydra.log.wrapper.withTag

private const val SERVICE_TYPE = "_tether._tcp."
private val log = KydraLog.withTag(default = "MdnsDiscovery.Apple")

// Public entry points (start/stop/republish) acquire lifecycleLock for atomicity of
// precondition + state mutation. NSNetService and NSNetServiceBrowser delegate callbacks
// are delivered on the run-loop thread that owns the service/browser — they do not acquire
// the lock.
actual class MdnsDiscovery(
    private val store: DiscoveredDevicesStore,
    private val deviceIdentityStore: DeviceIdentityStore,
) : DeviceDiscovery {
    actual override val discoveredDevices: StateFlow<List<Device>> get() = store.devices

    private val lifecycleLock = Mutex()

    private var fingerprint: String = ""
    private var netService: NSNetService? = null
    private var browser: NSNetServiceBrowser? = null
    private var ownServiceName: String? = null
    private var currentPort: Int = 0

    // Strong references to delegates — ObjC delegate properties are weak, so without
    // these Kotlin fields the delegates would be GC'd before their callbacks fire.
    private var serviceDelegate: ServiceDelegate? = null
    private var browserDelegate: BrowserDelegate? = null
    private val resolutionDelegates = mutableListOf<ResolutionDelegate>()

    actual override suspend fun start(deviceName: String, port: Int) {
        val fingerprint = deviceIdentityStore.getOrCreate()
        lifecycleLock.withLock {
            if (netService != null || browser != null) {
                throw IllegalStateException("MdnsDiscovery already started; call stop() first")
            }

            this.fingerprint = fingerprint
            ownServiceName = deviceName
            currentPort = port

            val service = NSNetService(
                domain = "",
                type = SERVICE_TYPE,
                name = deviceName,
                port = port,
            )

            val delegate = ServiceDelegate(this)
            serviceDelegate = delegate
            service.delegate = delegate
            service.setTXTRecordData(txtRecordData())
            service.publish()
            netService = service

            log.info { "publishing service $deviceName on port $port" }

            val newBrowserDelegate = BrowserDelegate(this)
            this.browserDelegate = newBrowserDelegate

            val newBrowser = NSNetServiceBrowser()
            newBrowser.delegate = newBrowserDelegate
            newBrowser.searchForServicesOfType(SERVICE_TYPE, inDomain = "")
            this.browser = newBrowser

            log.info { "started discovery for $SERVICE_TYPE" }
        }
    }

    actual override suspend fun stop() {
        lifecycleLock.withLock {
            try {
                netService?.stop()
            } catch (e: Exception) {
                log.warn { "failed to stop service — ${e.message ?: "unknown error"}" }
            }

            try {
                browser?.stop()
            } catch (e: Exception) {
                log.warn { "failed to stop browser — ${e.message ?: "unknown error"}" }
            }

            netService = null
            browser = null
            ownServiceName = null
            currentPort = 0
            serviceDelegate = null
            browserDelegate = null
            resolutionDelegates.clear()
            store.clear()

            log.info { "stopped" }
        }
    }

    /**
     * In-flight ResolutionDelegate entries are NOT cleared here — their NSNetService.delegate
     * is a weak ObjC ref; clearing the strong Kotlin ref would create a GC window.
     * Each delegate self-removes on resolve/timeout.
     */
    actual override suspend fun republish(name: String) {
        lifecycleLock.withLock {
            if (netService == null && browser == null) return
            log.info { "republishing as $name" }
            try {
                netService?.stop()
            } catch (e: Exception) {
                log.warn { "failed to stop service for republish — ${e.message ?: "unknown error"}" }
            }
            ownServiceName = name

            val service = NSNetService(
                domain = "",
                type = SERVICE_TYPE,
                name = name,
                port = currentPort,
            )
            val delegate = ServiceDelegate(this)
            serviceDelegate = delegate
            service.delegate = delegate
            service.setTXTRecordData(txtRecordData())
            service.publish()
            netService = service

            log.info { "republished service $name on port $currentPort" }
        }
    }

    private fun txtRecordData(): NSData? {
        val dict: Map<String, NSData> = txtProps(fingerprint).entries.associate { (k, v) ->
            k to (v.encodeToByteArray().toNSData() ?: NSData())
        }
        // Safe: Kotlin/Native ObjC bridge maps NSDictionary* parameters to Map<Any?, *>;
        // our keys (String) and values (NSData) are accepted by the bridge at runtime.
        @Suppress("UNCHECKED_CAST")
        return NSNetService.dataFromTXTRecordDictionary(dict as Map<Any?, *>)
    }

    private fun onServiceFound(service: NSNetService) {
        val serviceName = service.name
        if (serviceName == ownServiceName) {
            log.debug { "ignoring self-discovery for $serviceName" }
            return
        }

        log.debug { "found service $serviceName, resolving..." }
        val delegate = ResolutionDelegate(this, serviceName)
        resolutionDelegates.add(delegate)
        service.delegate = delegate
        service.resolveWithTimeout(5.0)
    }

    private fun onServiceRemoved(service: NSNetService) {
        val serviceName = service.name
        log.info { "service removed $serviceName" }
        store.removeByName(serviceName)
    }

    private fun onServiceResolved(service: NSNetService) {
        val serviceName = service.name
        log.debug { "resolved service $serviceName" }

        val port = service.port.toInt()
        if (port !in 1..65535) {
            log.warn { "invalid port $port for $serviceName, skipping" }
            return
        }

        val host = service.hostName
        if (host.isNullOrEmpty()) {
            log.warn { "could not get hostname for $serviceName, skipping" }
            return
        }

        val peerFingerprint = extractFingerprintFromTxt(service)

        val device = Device(
            name = serviceName,
            host = host,
            port = port,
            fingerprint = peerFingerprint,
        )

        log.info { "peer discovered: ${device.name}@${device.host}:${device.port}" }
        store.upsert(device)
    }

    private fun extractFingerprintFromTxt(service: NSNetService): String? {
        val txtData = service.TXTRecordData() ?: return null
        val dict = NSNetService.dictionaryFromTXTRecordData(txtData)
        val valueData = dict["fp"] as? NSData ?: return null
        val length = valueData.length.toInt()
        if (length == 0) return null
        val rawPtr = valueData.bytes ?: return null
        val bytesPtr = interpretCPointer<ByteVar>(rawPtr.rawValue) ?: return null
        return bytesPtr.readBytes(length).decodeToString()
    }

    private fun onServiceResolutionFailed(serviceName: String) {
        log.warn { "failed to resolve service $serviceName" }
    }

    // Kotlin/Native maps ObjC "method:arg1:arg2:" to fun method(arg1, arg2).
    private class ServiceDelegate(
        private val discovery: MdnsDiscovery,
    ) : NSObject(),
        NSNetServiceDelegateProtocol {
        override fun netServiceDidPublish(sender: NSNetService) {
            log.info { "service published: ${sender.name}" }
            discovery.ownServiceName = sender.name
        }

        @ObjCSignatureOverride
        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        override fun netService(sender: NSNetService, didNotPublish: Map<Any?, *>) {
            log.warn { "service publication failed: ${sender.name}" }
        }

        override fun netServiceDidStop(sender: NSNetService) {
            log.info { "service stopped: ${sender.name}" }
        }

        override fun netServiceDidResolveAddress(sender: NSNetService) {
            discovery.onServiceResolved(sender)
        }

        @ObjCSignatureOverride
        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        override fun netService(sender: NSNetService, didNotResolve: Map<Any?, *>) {
            discovery.onServiceResolutionFailed(sender.name)
        }
    }

    // Kotlin/Native maps ObjC "method:arg1:arg2:" to fun method(arg1, arg2).
    private class BrowserDelegate(
        private val discovery: MdnsDiscovery,
    ) : NSObject(),
        NSNetServiceBrowserDelegateProtocol {
        @ObjCSignatureOverride
        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        override fun netServiceBrowser(
            aNetServiceBrowser: NSNetServiceBrowser,
            didFindService: NSNetService,
            moreComing: Boolean,
        ) {
            discovery.onServiceFound(didFindService)
        }

        @ObjCSignatureOverride
        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        override fun netServiceBrowser(
            aNetServiceBrowser: NSNetServiceBrowser,
            didRemoveService: NSNetService,
            moreComing: Boolean,
        ) {
            discovery.onServiceRemoved(didRemoveService)
        }

        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        override fun netServiceBrowser(
            aNetServiceBrowser: NSNetServiceBrowser,
            didNotSearch: Map<Any?, *>,
        ) {
            log.warn { "browser search failed" }
        }

        override fun netServiceBrowserDidStopSearch(browser: NSNetServiceBrowser) {
            log.info { "browser stopped" }
        }
    }

    // Separate delegate per discovered service to isolate resolution callbacks.
    private class ResolutionDelegate(
        private val discovery: MdnsDiscovery,
        private val serviceName: String,
    ) : NSObject(),
        NSNetServiceDelegateProtocol {
        override fun netServiceDidResolveAddress(sender: NSNetService) {
            discovery.onServiceResolved(sender)
            discovery.resolutionDelegates.remove(this)
        }

        @ObjCSignatureOverride
        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        override fun netService(sender: NSNetService, didNotResolve: Map<Any?, *>) {
            discovery.onServiceResolutionFailed(serviceName)
            discovery.resolutionDelegates.remove(this)
        }
    }
}
