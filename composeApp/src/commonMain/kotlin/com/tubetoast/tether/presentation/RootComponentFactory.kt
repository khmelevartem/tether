package com.tubetoast.tether.presentation

import com.arkivanov.decompose.ComponentContext
import com.tubetoast.tether.discovery.DeviceDiscovery
import com.tubetoast.tether.presentation.transfer.PeerTransferRepository

class RootComponentFactory(
    private val discovery: DeviceDiscovery,
    private val peerTransferRepository: PeerTransferRepository,
) {
    fun create(componentContext: ComponentContext): RootComponent =
        RootComponent(
            componentContext = componentContext,
            deviceListFactory = { ctx ->
                DeviceListComponent(componentContext = ctx, discovery = discovery)
            },
            transferDetailsFactory = { _, _ ->
                error(
                    "TODO(#191): wire to per-peer TransferDetailsComponent via PeerTransferComponent registry",
                )
            },
        )
}
