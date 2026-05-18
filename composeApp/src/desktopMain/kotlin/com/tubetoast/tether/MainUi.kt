package com.tubetoast.tether

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.tubetoast.tether.di.DefaultDesktopAppConfig
import com.tubetoast.tether.di.DesktopAppContainer
import com.tubetoast.tether.presentation.RootComponent
import com.tubetoast.tether.transfer.DesktopFilePicker
import com.tubetoast.tether.transfer.JvmFileSource
import com.tubetoast.tether.transfer.walk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import tether.composeapp.generated.resources.Res
import tether.composeapp.generated.resources.icon
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.awt.dnd.DropTargetListener
import java.io.File
import java.io.IOException
import kotlin.io.path.toPath

fun main() {
    val deviceName = defaultDesktopDeviceName()
    val container = DesktopAppContainer(
        DefaultDesktopAppConfig(
            deviceName = deviceName,
            port = 0,
        ),
    )
    container.startBackendOrFail(deviceName)
    container.registerShutdownHook()

    val lifecycle = LifecycleRegistry()
    val root = RootComponent(
        componentContext = DefaultComponentContext(lifecycle),
        discovery = container.mdnsDiscovery,
        fileClient = container.fileClient,
        filePicker = DesktopFilePicker(),
    )
    lifecycle.resume()

    val ioScope = CoroutineScope(Dispatchers.IO)

    application {
        Window(
            onCloseRequest = {
                if (root.canExitNow()) {
                    lifecycle.destroy()
                    exitApplication()
                } else {
                    val activeChild = root.stack.value.active.instance
                    if (activeChild is RootComponent.Child.TransferChild) {
                        activeChild.component.onBackPressed()
                    }
                }
            },
            title = "Tether",
            icon = painterResource(Res.drawable.icon),
        ) {
            App(root)

            window.dropTarget = DropTarget(
                window,
                DnDConstants.ACTION_COPY,
                object : DropTargetListener {
                    override fun dragEnter(dtde: DropTargetDragEvent) {
                        dtde.acceptDrag(DnDConstants.ACTION_COPY)
                        val activeChild = root.stack.value.active.instance
                        if (activeChild is RootComponent.Child.DeviceListChild) {
                            activeChild.component.onDragHoverChanged(true)
                        } else {
                            root.onDragHoverChanged(true)
                        }
                    }

                    override fun dragOver(dtde: DropTargetDragEvent) {
                        dtde.acceptDrag(DnDConstants.ACTION_COPY)
                    }

                    override fun dropActionChanged(dtde: DropTargetDragEvent) {}

                    override fun dragExit(dte: DropTargetEvent) {
                        val activeChild = root.stack.value.active.instance
                        if (activeChild is RootComponent.Child.DeviceListChild) {
                            activeChild.component.onDragHoverChanged(false)
                        } else {
                            root.onDragHoverChanged(false)
                        }
                    }

                    override fun drop(dtde: DropTargetDropEvent) {
                        root.onDragHoverChanged(false)
                        val activeChild = root.stack.value.active.instance
                        if (activeChild is RootComponent.Child.TransferChild) {
                            dtde.acceptDrop(DnDConstants.ACTION_COPY)
                            root.onDropRejectedDuringTransfer()
                            dtde.dropComplete(false)
                            return
                        }
                        dtde.acceptDrop(DnDConstants.ACTION_COPY)
                        if (activeChild is RootComponent.Child.DeviceListChild) {
                            activeChild.component.onDragHoverChanged(false)
                        }
                        val transferable = dtde.transferable
                        if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                            dtde.dropComplete(false)
                            return
                        }
                        @Suppress("UNCHECKED_CAST")
                        val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                        ioScope.launch {
                            val sources = try {
                                files.flatMap { file ->
                                    val path = file.toPath()
                                    if (file.isDirectory) walk(path) else listOf(JvmFileSource(path))
                                }
                            } catch (e: IOException) {
                                withContext(Dispatchers.Main) { dtde.dropComplete(false) }
                                return@launch
                            }
                            withContext(Dispatchers.Main) {
                                root.onDroppedFiles(sources)
                                dtde.dropComplete(true)
                            }
                        }
                    }
                },
            )
        }
    }
}
