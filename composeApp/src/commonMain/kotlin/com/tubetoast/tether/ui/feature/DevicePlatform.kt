package com.tubetoast.tether.ui.feature

import androidx.compose.ui.graphics.vector.ImageVector
import compose.icons.TablerIcons
import compose.icons.tablericons.DeviceDesktop
import compose.icons.tablericons.DeviceLaptop
import compose.icons.tablericons.DeviceMobile
import compose.icons.tablericons.DeviceTablet

enum class DevicePlatform { Laptop, Smartphone, Tablet, Desktop }

fun DevicePlatform.toTablerIcon(): ImageVector = when (this) {
    DevicePlatform.Laptop -> TablerIcons.DeviceLaptop
    DevicePlatform.Smartphone -> TablerIcons.DeviceMobile
    DevicePlatform.Tablet -> TablerIcons.DeviceTablet
    DevicePlatform.Desktop -> TablerIcons.DeviceDesktop
}

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
