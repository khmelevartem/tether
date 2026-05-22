package com.tubetoast.tether.discovery

import com.tubetoast.tether.protocol.Device
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.flow.StateFlow
import platform.Foundation.NSLog
import platform.Foundation.NSNetService
import platform.Foundation.NSNetServiceBrowser
import platform.Foundation.NSNetServiceBrowserDelegateProtocol
import platform.Foundation.NSNetServiceDelegateProtocol
import platform.darwin.NSObject

private const val SERVICE_TYPE = "_tether._tcp."

// Thread-safety note: NSNetService and NSNetServiceBrowser must be created and used on
// the thread that owns their run loop. All callbacks (didPublish, didFind, didResolve)
// are delivered on that same run loop thread. As long as start() and stop() are called
// from the same thread (main thread via DisposableEffect in Compose), all accesses to
// mutable state are single-threaded — no @Synchronized or @Volatile needed.
// @Synchronized is a JVM-only annotation and is not available in Kotlin/Native.
actual class MdnsDiscovery(
    private val store: DiscoveredDevicesStore,
) : DeviceDiscovery {
    actual override val discoveredDevices: StateFlow<List<Device>> get() = store.devices

    private var netService: NSNetService? = null
    private var browser: NSNetServiceBrowser? = null
    private var ownServiceName: String? = null
    private var currentPort: Int = 0

    // Strong references to delegates — ObjC delegate properties are weak, so without
    // these Kotlin fields the delegates would be GC'd before their callbacks fire.
    private var serviceDelegate: ServiceDelegate? = null
    private var browserDelegate: BrowserDelegate? = null
    private val resolutionDelegates = mutableListOf<ResolutionDelegate>()

    actual override fun start(deviceName: String, port: Int) {
        if (netService != null || browser != null) {
            throw IllegalStateException("MdnsDiscovery already started; call stop() first")
        }

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
        service.publish()
        netService = service

        NSLog("mDNS: publishing service %s on port %d", deviceName, port)

        val browserDelegate = BrowserDelegate(this)
        this.browserDelegate = browserDelegate

        val browser = NSNetServiceBrowser()
        browser.delegate = browserDelegate
        browser.searchForServicesOfType(SERVICE_TYPE, inDomain = "")
        this.browser = browser

        NSLog("mDNS: started discovery for %s", SERVICE_TYPE)
    }

    actual override fun stop() {
        try {
            netService?.stop()
        } catch (e: Exception) {
            NSLog("mDNS: failed to stop service — %s", e.message ?: "unknown error")
        }

        try {
            browser?.stop()
        } catch (e: Exception) {
            NSLog("mDNS: failed to stop browser — %s", e.message ?: "unknown error")
        }

        netService = null
        browser = null
        ownServiceName = null
        currentPort = 0
        serviceDelegate = null
        browserDelegate = null
        resolutionDelegates.clear()
        store.clear()

        NSLog("mDNS: stopped")
    }

    /**
     * In-flight ResolutionDelegate entries are NOT cleared here — their NSNetService.delegate
     * is a weak ObjC ref; clearing the strong Kotlin ref would create a GC window.
     * Each delegate self-removes on resolve/timeout.
     */
    actual override fun republish(name: String) {
        if (netService == null && browser == null) return
        NSLog("mDNS: republishing as %s", name)
        try {
            netService?.stop()
        } catch (e: Exception) {
            NSLog("mDNS: failed to stop service for republish — %s", e.message ?: "unknown error")
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
        service.publish()
        netService = service

        NSLog("mDNS: republished service %s on port %d", name, currentPort)
    }

    private fun onServiceFound(service: NSNetService) {
        val serviceName = service.name
        if (serviceName == ownServiceName) {
            NSLog("mDNS: ignoring self-discovery for %s", serviceName)
            return
        }

        NSLog("mDNS: found service %s, resolving...", serviceName)
        val delegate = ResolutionDelegate(this, serviceName)
        resolutionDelegates.add(delegate)
        service.delegate = delegate
        service.resolveWithTimeout(5.0)
    }

    private fun onServiceRemoved(service: NSNetService) {
        val serviceName = service.name
        NSLog("mDNS: service removed %s", serviceName)
        store.removeByName(serviceName)
    }

    private fun onServiceResolved(service: NSNetService) {
        val serviceName = service.name
        NSLog("mDNS: resolved service %s", serviceName)

        val port = service.port.toInt()
        if (port !in 1..65535) {
            NSLog("mDNS: invalid port %d for %s, skipping", port, serviceName)
            return
        }

        val host = service.hostName
        if (host.isNullOrEmpty()) {
            NSLog("mDNS: could not get hostname for %s, skipping", serviceName)
            return
        }

        val device = Device(
            name = serviceName,
            host = host,
            port = port,
        )

        NSLog("mDNS: peer discovered: %s@%s:%d", device.name, device.host, device.port)
        store.upsert(device)
    }

    private fun onServiceResolutionFailed(serviceName: String) {
        NSLog("mDNS: failed to resolve service %s", serviceName)
    }

    // Kotlin/Native maps ObjC "method:arg1:arg2:" to fun method(arg1, arg2).
    private class ServiceDelegate(
        private val discovery: MdnsDiscovery,
    ) : NSObject(),
        NSNetServiceDelegateProtocol {
        override fun netServiceDidPublish(sender: NSNetService) {
            NSLog("mDNS: service published: %s", sender.name)
            discovery.ownServiceName = sender.name
        }

        @ObjCSignatureOverride
        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        override fun netService(sender: NSNetService, didNotPublish: Map<Any?, *>) {
            NSLog("mDNS: service publication failed: %s", sender.name)
        }

        override fun netServiceDidStop(sender: NSNetService) {
            NSLog("mDNS: service stopped: %s", sender.name)
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
            NSLog("mDNS: browser search failed")
        }

        override fun netServiceBrowserDidStopSearch(browser: NSNetServiceBrowser) {
            NSLog("mDNS: browser stopped")
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
