package com.tubetoast.tether.ui.feature

import androidx.compose.ui.graphics.vector.ImageVector
import com.tubetoast.tether.protocol.DevicePlatform
import compose.icons.TablerIcons
import compose.icons.tablericons.DeviceDesktop
import compose.icons.tablericons.DeviceLaptop
import compose.icons.tablericons.DeviceMobile
import compose.icons.tablericons.DeviceTablet

fun DevicePlatform.toTablerIcon(): ImageVector = when (this) {
    DevicePlatform.Laptop -> TablerIcons.DeviceLaptop
    DevicePlatform.Smartphone -> TablerIcons.DeviceMobile
    DevicePlatform.Tablet -> TablerIcons.DeviceTablet
    DevicePlatform.Desktop -> TablerIcons.DeviceDesktop
}
