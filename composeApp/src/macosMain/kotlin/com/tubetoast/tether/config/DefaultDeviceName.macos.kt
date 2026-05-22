package com.tubetoast.tether.config

import platform.Foundation.NSHost

actual fun defaultDeviceName(): String {
    // NSHost is deprecated since macOS 13, but localizedName is the only API that returns the
    // System-Settings Computer Name. ProcessInfo.hostName returns a different value.
    @Suppress("DEPRECATION")
    val host = NSHost.currentHost()
    return (host.localizedName ?: host.name)?.takeIf { it.isNotBlank() } ?: "Mac"
}
