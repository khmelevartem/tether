package com.tubetoast.tether.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.tubetoast.tether.discovery.DeviceDiscovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class DeviceListComponent(
    componentContext: ComponentContext,
    private val discovery: DeviceDiscovery,
    coroutineScope: CoroutineScope = componentContext.coroutineScope(),
) : ComponentContext by componentContext {
    private val _state = MutableValue(DeviceListState.empty())
    val state: Value<DeviceListState> = _state

    init {
        coroutineScope.launch {
            discovery.discoveredDevices.collect { devices ->
                _state.update { DeviceListState(devices) }
            }
        }
    }
}
