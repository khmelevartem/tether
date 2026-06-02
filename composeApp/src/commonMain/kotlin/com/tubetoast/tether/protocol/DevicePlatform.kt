package com.tubetoast.tether.protocol

enum class DevicePlatform { Laptop, Smartphone, Tablet, Desktop }

fun inferDevicePlatform(name: String): DevicePlatform? {
    val lower = name.lowercase()
    return when {
        "laptop" in lower || "macbook" in lower || "book" in lower -> DevicePlatform.Laptop
        "ipad" in lower || "tablet" in lower -> DevicePlatform.Tablet
        "phone" in lower || "iphone" in lower || "pixel" in lower -> DevicePlatform.Smartphone
        "desktop" in lower || "pc" in lower || "imac" in lower || "mac" in lower -> DevicePlatform.Desktop
        else -> null
    }
}
