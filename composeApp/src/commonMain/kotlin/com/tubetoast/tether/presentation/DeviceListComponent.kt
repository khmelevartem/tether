package com.tubetoast.tether.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.tubetoast.tether.discovery.DeviceDiscovery
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.FilePickerProvider
import com.tubetoast.tether.transfer.FileSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class DeviceListComponent(
    componentContext: ComponentContext,
    private val discovery: DeviceDiscovery,
    val pendingFiles: Value<List<FileSource>> = MutableValue(emptyList()),
    private val filePickerProvider: FilePickerProvider = FilePickerProvider { null },
    private val onSendRequested: (Device, List<FileSource>) -> Unit = { _, _ -> },
    private val scope: CoroutineScope = componentContext.coroutineScope(),
) : ComponentContext by componentContext {
    private val _state = MutableValue(DeviceListState.empty())
    val state: Value<DeviceListState> = _state

    init {
        scope.launch {
            discovery.discoveredDevices.collect { devices ->
                _state.update { it.copy(devices = devices) }
            }
        }
        pendingFiles.subscribe { files ->
            _state.update { it.copy(pendingFiles = files) }
        }
    }

    fun onDeviceClicked(device: Device) {
        val pending = state.value.pendingFiles
        if (pending.isNotEmpty()) {
            onSendRequested(device, pending)
            return
        }
        if (filePickerProvider.current() != null) {
            _state.update { it.copy(sendChooserTarget = device) }
        }
    }

    fun onSendFiles(device: Device) {
        _state.update { it.copy(sendChooserTarget = null) }
        scope.launch {
            val sources = filePickerProvider.current()?.pickFiles(multi = true) ?: return@launch
            if (sources.isNotEmpty()) {
                onSendRequested(device, sources)
            }
        }
    }

    fun onSendFolder(device: Device) {
        _state.update { it.copy(sendChooserTarget = null) }
        scope.launch {
            val sources = filePickerProvider.current()?.pickFolder() ?: return@launch
            if (sources.isNotEmpty()) {
                onSendRequested(device, sources)
            }
        }
    }

    fun onDismissChooser() {
        _state.update { it.copy(sendChooserTarget = null) }
    }

    fun onDragHoverChanged(hovering: Boolean) {
        _state.update { it.copy(isDragHover = hovering, dragRejected = false) }
    }

    fun onDragRejected() {
        _state.update { it.copy(isDragHover = true, dragRejected = true) }
    }
}
