@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.tubetoast.tether

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.tubetoast.tether.di.DefaultDesktopAppConfig
import com.tubetoast.tether.di.DesktopAppContainer
import com.tubetoast.tether.logging.initTetherLogging
import com.tubetoast.tether.logging.isDebugEnabled
import com.tubetoast.tether.presentation.RootScreen
import com.tubetoast.tether.transfer.toFileSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.warn
import ru.pocketbyte.kydra.log.wrapper.withTag
import tether.composeapp.generated.resources.Res
import tether.composeapp.generated.resources.icon
import java.net.URI
import java.nio.file.Path

private val log = KydraLog.withTag(default = "MainUi")

fun main() = runBlocking {
    // see docs/knowledge/desktop-system-theme.md — must be set before any Swing/AWT class loads
    System.setProperty("apple.awt.application.appearance", "system")
    initTetherLogging(debugEnabled = isDebugEnabled())
    val container = DesktopAppContainer(
        DefaultDesktopAppConfig(port = 0),
    )
    container.nameStore.init()
    val handle = container.startBackendOrFail()
    container.autoSendDispatcher.start()
    registerShutdownHook(handle)

    val lifecycle = LifecycleRegistry()
    val component = container.rootComponentFactory.create(DefaultComponentContext(lifecycle))
    lifecycle.resume()

    application {
        ObservedSystemTheme {
            Window(
                onCloseRequest = {
                    lifecycle.destroy()
                    exitApplication()
                },
                title = "Tether",
                icon = painterResource(Res.drawable.icon),
            ) {
                val scope = rememberCoroutineScope()

                LaunchedEffect(window) {
                    container.windowHolder.window = window
                }

                val dropTarget = remember(component) {
                    object : DragAndDropTarget {
                        override fun onEntered(event: DragAndDropEvent) {
                            component.onDragEntered()
                        }

                        override fun onExited(event: DragAndDropEvent) {
                            component.onDragExited()
                        }

                        override fun onEnded(event: DragAndDropEvent) {
                            component.onDragExited()
                        }

                        override fun onDrop(event: DragAndDropEvent): Boolean {
                            val uris = (event.dragData() as? DragData.FilesList)
                                ?.readFiles()
                                .orEmpty()
                            if (uris.isEmpty()) {
                                component.onDragExited()
                                return false
                            }
                            scope.launch {
                                val sources = withContext(Dispatchers.IO) {
                                    uris.flatMap { uri ->
                                        val path = runCatching { Path.of(URI(uri)) }.getOrNull()
                                            ?: return@flatMap emptyList()
                                        runCatching { path.toFile().toFileSources() }
                                            .onFailure { log.warn { "onDrop: skipping $uri — ${it.message}" } }
                                            .getOrElse { emptyList() }
                                    }
                                }
                                component.onFilesDropped(sources)
                            }
                            log.info { "onDrop: accepted ${uris.size} URI(s)" }
                            return true
                        }
                    }
                }

                RootScreen(
                    component = component,
                    modifier = Modifier.dragAndDropTarget(
                        shouldStartDragAndDrop = { true },
                        target = dropTarget,
                    ),
                )
            }
        }
    }
}
