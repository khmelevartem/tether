package com.tubetoast.tether.presentation

import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.FileSource

data class DeviceListState(
    val devices: List<Device>,
    val pendingFiles: List<FileSource> = emptyList(),
    val isDragHover: Boolean = false,
    val dragRejected: Boolean = false,
) {
    companion object {
        fun empty() = DeviceListState(emptyList())
    }
}
