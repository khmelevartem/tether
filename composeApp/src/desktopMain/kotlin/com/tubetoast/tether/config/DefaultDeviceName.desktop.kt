package com.tubetoast.tether.config

actual fun defaultDeviceName(): String {
    val hostname = runCatching {
        java.net.InetAddress
            .getLocalHost()
            .hostName
    }.getOrNull()
    if (!hostname.isNullOrBlank() && hostname != "localhost" && !hostname.startsWith("127.")) {
        return hostname
    }
    val user = System.getenv("USER") ?: System.getProperty("user.name")
    return if (!user.isNullOrBlank()) "$user's Desktop" else "Desktop"
}
