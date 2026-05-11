package com.tubetoast.tether.presentation

import com.tubetoast.tether.protocol.Device

data class DeviceListState(
    val devices: List<Device>,
) {
    companion object {
        fun empty() = DeviceListState(emptyList())
    }
}
