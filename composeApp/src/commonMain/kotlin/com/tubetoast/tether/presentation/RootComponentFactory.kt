package com.tubetoast.tether.presentation

import com.arkivanov.decompose.ComponentContext
import com.tubetoast.tether.discovery.DeviceDiscovery

class RootComponentFactory(
    private val discovery: DeviceDiscovery,
) {
    fun create(componentContext: ComponentContext): RootComponent =
        RootComponent(
            componentContext = componentContext,
            deviceListFactory = { ctx ->
                DeviceListComponent(componentContext = ctx, discovery = discovery)
            },
            transferDetailsFactory = { _, peer -> TransferDetailsComponent(peer) },
        )
}
