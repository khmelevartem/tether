package com.tubetoast.tether.presentation

import com.tubetoast.tether.presentation.transfer.PeerRowProjection
import com.tubetoast.tether.presentation.transfer.PeerTransferState
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.PeerIdentity
import com.tubetoast.tether.transfer.toPeerIdentity

data class DeviceRow(
    val device: Device,
    val transferState: PeerTransferState,
    val isOnline: Boolean,
)

data class DeviceListState(
    val devices: List<Device>,
    val rows: List<DeviceRow>,
) {
    companion object {
        fun empty() = DeviceListState(emptyList(), emptyList())
    }
}

fun List<Device>.toRows(projections: Map<PeerIdentity, PeerRowProjection>): List<DeviceRow> =
    map { device ->
        val projection = projections[device.toPeerIdentity()]
        DeviceRow(
            device = device,
            transferState = projection?.state ?: PeerTransferState.Idle(device.toPeerIdentity()),
            isOnline = projection?.isOnline ?: true,
        )
    }
