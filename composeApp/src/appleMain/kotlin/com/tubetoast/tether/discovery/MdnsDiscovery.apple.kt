package com.tubetoast.tether.discovery

import com.tubetoast.tether.protocol.Device
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSLog
import platform.Foundation.NSNetService
import platform.Foundation.NSNetServiceBrowser

private const val SERVICE_TYPE = "_tether._tcp."

actual class MdnsDiscovery actual constructor() {
    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    actual val discoveredDevices: Flow<List<Device>> = _discoveredDevices.asStateFlow()

    private var netService: NSNetService? = null
    private var browser: NSNetServiceBrowser? = null
    private var ownServiceName: String? = null
    private var browserDelegate: BrowserDelegate? = null

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
        @Suppress("UNCHECKED_CAST", "TYPE_MISMATCH")
        service.delegate = delegate as platform.Foundation.NSNetServiceDelegateProtocol
        service.publish()
        netService = service

        NSLog("mDNS: publishing service %s on port %d", deviceName, port)

        val browserDelegate = BrowserDelegate(this)
        this.browserDelegate = browserDelegate

        val browser = NSNetServiceBrowser()
        @Suppress("UNCHECKED_CAST", "TYPE_MISMATCH")
        browser.delegate = browserDelegate as platform.Foundation.NSNetServiceBrowserDelegateProtocol
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
        browserDelegate = null
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
        @Suppress("UNCHECKED_CAST", "TYPE_MISMATCH")
        service.delegate = delegate as platform.Foundation.NSNetServiceDelegateProtocol
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

    private class ServiceDelegate(
        private val discovery: MdnsDiscovery,
    ) {
        fun netServiceDidPublish(sender: NSNetService) {
            NSLog("mDNS: service published: %s", sender.name)
            discovery.ownServiceName = sender.name
        }

        fun netServiceDidNotPublish(sender: NSNetService, errorDict: Map<*, *>) {
            NSLog("mDNS: service publication failed: %s", sender.name)
        }

        fun netServiceDidStop(sender: NSNetService) {
            NSLog("mDNS: service stopped: %s", sender.name)
        }

        fun netServiceDidResolveAddress(sender: NSNetService) {
            discovery.onServiceResolved(sender)
        }

        fun netServiceDidNotResolve(sender: NSNetService, errorDict: Map<*, *>) {
            discovery.onServiceResolutionFailed(sender.name)
        }
    }

    private class BrowserDelegate(
        private val discovery: MdnsDiscovery,
    ) {
        fun netServiceBrowserDidFindService(
            aNetServiceBrowser: NSNetServiceBrowser,
            netService: NSNetService,
            moreComing: Boolean,
        ) {
            discovery.onServiceFound(netService)
        }

        fun netServiceBrowserDidRemoveService(
            aNetServiceBrowser: NSNetServiceBrowser,
            netService: NSNetService,
            moreComing: Boolean,
        ) {
            discovery.onServiceRemoved(netService.name)
        }

        fun netServiceBrowserDidNotSearch(aNetServiceBrowser: NSNetServiceBrowser, errorDict: Map<*, *>) {
            NSLog("mDNS: browser search failed")
        }

        fun netServiceBrowserDidStopSearch(aNetServiceBrowser: NSNetServiceBrowser) {
            NSLog("mDNS: browser stopped")
        }
    }

    private class ResolutionDelegate(
        private val discovery: MdnsDiscovery,
        private val serviceName: String,
    ) {
        fun netServiceDidResolveAddress(sender: NSNetService) {
            discovery.onServiceResolved(sender)
        }

        fun netServiceDidNotResolve(sender: NSNetService, errorDict: Map<*, *>) {
            discovery.onServiceResolutionFailed(serviceName)
        }
    }
}
