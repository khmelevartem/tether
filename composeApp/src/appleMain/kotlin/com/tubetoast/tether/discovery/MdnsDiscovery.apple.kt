package com.tubetoast.tether.discovery

import com.tubetoast.tether.protocol.Device
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
actual class MdnsDiscovery actual constructor() {
    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    actual val discoveredDevices: StateFlow<List<Device>> = _discoveredDevices.asStateFlow()

    private var netService: NSNetService? = null
    private var browser: NSNetServiceBrowser? = null
    private var ownServiceName: String? = null

    // Strong references to delegates — ObjC delegate properties are weak, so without
    // these Kotlin fields the delegates would be GC'd before their callbacks fire.
    private var serviceDelegate: ServiceDelegate? = null
    private var browserDelegate: BrowserDelegate? = null
    private val resolutionDelegates = mutableListOf<ResolutionDelegate>()

    actual fun start(deviceName: String, port: Int) {
        if (netService != null || browser != null) {
            throw IllegalStateException("MdnsDiscovery already started; call stop() first")
        }

        ownServiceName = deviceName

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

    actual fun stop() {
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
        serviceDelegate = null
        browserDelegate = null
        resolutionDelegates.clear()
        _discoveredDevices.value = emptyList()

        NSLog("mDNS: stopped")
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

    private fun onServiceRemoved(serviceName: String) {
        NSLog("mDNS: service removed %s", serviceName)
        _discoveredDevices.value = _discoveredDevices.value
            .filterNot { it.name == serviceName }
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
            id = "$serviceName@$host:$port",
            name = serviceName,
            host = host,
            port = port,
        )

        NSLog("mDNS: peer discovered: %s@%s:%d", device.name, device.host, device.port)
        _discoveredDevices.value = _discoveredDevices.value
            .filterNot { it.name == device.name } + device
    }

    private fun onServiceResolutionFailed(serviceName: String) {
        NSLog("mDNS: failed to resolve service %s", serviceName)
    }

    // NSNetService delegate: handles service publication and resolution callbacks.
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

    // NSNetServiceBrowser delegate: handles service discovery callbacks.
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
            discovery.onServiceRemoved(didRemoveService.name)
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
        }

        @ObjCSignatureOverride
        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        override fun netService(sender: NSNetService, didNotResolve: Map<Any?, *>) {
            discovery.onServiceResolutionFailed(serviceName)
        }
    }
}
