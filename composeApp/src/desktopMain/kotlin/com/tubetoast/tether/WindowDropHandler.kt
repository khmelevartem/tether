@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.tubetoast.tether

import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import com.tubetoast.tether.presentation.RootComponent
import com.tubetoast.tether.transfer.toFileSources
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.warn
import ru.pocketbyte.kydra.log.wrapper.withTag
import java.net.URI
import java.nio.file.Path

private val log = KydraLog.withTag(default = "WindowDropHandler")

internal class WindowDropHandler(
    private val component: RootComponent,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val target: DragAndDropTarget = object : DragAndDropTarget {
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
                val sources = withContext(ioDispatcher) {
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
