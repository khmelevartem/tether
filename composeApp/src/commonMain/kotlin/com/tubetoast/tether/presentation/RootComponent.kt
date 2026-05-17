@file:OptIn(com.arkivanov.decompose.DelicateDecomposeApi::class)

package com.tubetoast.tether.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.tubetoast.tether.discovery.DeviceDiscovery
import com.tubetoast.tether.network.FileClient
import com.tubetoast.tether.presentation.transfer.BatchSender
import com.tubetoast.tether.presentation.transfer.TransferComponent
import com.tubetoast.tether.presentation.transfer.TransferState
import com.tubetoast.tether.protocol.Device
import com.tubetoast.tether.transfer.FilePicker
import com.tubetoast.tether.transfer.FileSource
import kotlinx.serialization.Serializable

class RootComponent(
    componentContext: ComponentContext,
    private val discovery: DeviceDiscovery,
    private val fileClient: FileClient,
    private val filePicker: FilePicker?,
) : ComponentContext by componentContext {
    private val navigation = StackNavigation<Config>()
    private val _pendingFiles = MutableValue<List<FileSource>>(emptyList())
    val pendingFiles: Value<List<FileSource>> = _pendingFiles
    private val _dragRejectedOverlay = MutableValue(false)
    val dragRejectedOverlay: Value<Boolean> = _dragRejectedOverlay

    // Sources are live objects (open file handles) — can't survive process death.
    // Config.Transfer stores a stable key; live sources are kept here in memory.
    private val sourcesRegistry = mutableMapOf<String, List<FileSource>>()

    val stack: Value<ChildStack<Config, Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.DeviceList,
        handleBackButton = true,
        childFactory = ::createChild,
    )

    fun onDeviceClicked(device: Device, sources: List<FileSource>) {
        _pendingFiles.value = emptyList()
        val key = device.id
        sourcesRegistry[key] = sources
        navigation.push(Config.Transfer(device, key))
    }

    fun onPendingIntent(sources: List<FileSource>) = setPendingFiles(sources)

    fun onDroppedFiles(sources: List<FileSource>) = setPendingFiles(sources)

    private fun setPendingFiles(sources: List<FileSource>) {
        _pendingFiles.value = sources
        if (stack.value.active.configuration !is Config.DeviceList) {
            navigation.pop()
        }
    }

    fun onDragHoverChanged(hovering: Boolean) {
        val activeChild = stack.value.active.instance
        if (activeChild is Child.TransferChild) {
            _dragRejectedOverlay.value = hovering
        }
    }

    fun canExitNow(): Boolean {
        val activeChild = stack.value.active.instance
        if (activeChild is Child.TransferChild) {
            val state = activeChild.component.state.value
            return state is TransferState.Terminal
        }
        return true
    }

    private fun createChild(config: Config, ctx: ComponentContext): Child = when (config) {
        is Config.DeviceList -> Child.DeviceListChild(
            DeviceListComponent(
                componentContext = ctx,
                discovery = discovery,
                pendingFiles = _pendingFiles,
                filePicker = filePicker,
                onSendRequested = ::onDeviceClicked,
            ),
        )

        is Config.Transfer -> {
            val sources = sourcesRegistry[config.sourcesKey] ?: emptyList()
            Child.TransferChild(
                TransferComponent(
                    componentContext = ctx,
                    peer = config.peer,
                    sources = sources,
                    batchSender = BatchSender(sendOne = { device, channel, name, size, onProgress ->
                        fileClient.send(device, channel, name, size, onProgress)
                    }),
                    onExit = {
                        sourcesRegistry.remove(config.sourcesKey)
                        navigation.pop()
                    },
                ),
            )
        }
    }

    @Serializable
    sealed class Config {
        @Serializable
        data object DeviceList : Config()

        @Serializable
        data class Transfer(
            val peer: Device,
            val sourcesKey: String,
        ) : Config()
    }

    sealed class Child {
        class DeviceListChild(
            val component: DeviceListComponent,
        ) : Child()

        class TransferChild(
            val component: TransferComponent,
        ) : Child()
    }
}
