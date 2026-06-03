package com.tubetoast.tether.ui.feature

import androidx.compose.ui.graphics.vector.ImageVector
import com.tubetoast.tether.protocol.DeviceType
import compose.icons.TablerIcons
import compose.icons.tablericons.DeviceDesktop
import compose.icons.tablericons.DeviceMobile
import compose.icons.tablericons.Terminal

fun DeviceType.toTablerIcon(): ImageVector = when (this) {
    DeviceType.Android -> TablerIcons.DeviceMobile
    DeviceType.Ios -> TablerIcons.DeviceMobile
    DeviceType.Desktop -> TablerIcons.DeviceDesktop
    DeviceType.Cli -> TablerIcons.Terminal
}
