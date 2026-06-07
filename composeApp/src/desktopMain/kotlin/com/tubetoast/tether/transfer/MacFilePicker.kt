package com.tubetoast.tether.transfer

import com.tubetoast.tether.platform.mac.Foundation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.wrapper.withTag
import java.io.File

private val log = KydraLog.withTag(default = "MacFilePicker")

internal class MacFilePicker(
    private val windowHolder: WindowHolder,
) : FilePicker {
    override suspend fun pickFiles(): List<FileSource> {
        val selected = pickFilesAndFolders()
        log.info { "pickFiles (mac): ${selected.size} item(s) selected" }
        return withContext(Dispatchers.IO) {
            selected.flatMap { it.toFileSources() }
        }
    }

    override suspend fun pickFolder(): List<FileSource> = jvmPickFolder(windowHolder)

    override suspend fun pickPhotos(): List<FileSource> =
        throw UnsupportedOperationException("pickPhotos is mobile-only")

    private companion object {
        private const val NS_MODAL_RESPONSE_OK = 1

        suspend fun pickFilesAndFolders(): List<File> = withContext(Dispatchers.IO) {
            val pool = Foundation.NSAutoreleasePool()
            try {
                var result: List<File> = emptyList()
                Foundation.executeOnMainThread(withAutoreleasePool = true, waitUntilDone = true) {
                    val panel = Foundation.invoke("NSOpenPanel", "openPanel")
                    Foundation.invoke(panel, "setCanChooseFiles:", true)
                    Foundation.invoke(panel, "setCanChooseDirectories:", true)
                    Foundation.invoke(panel, "setAllowsMultipleSelection:", true)
                    val response = Foundation.invoke(panel, "runModal")
                    if (response.toInt() == NS_MODAL_RESPONSE_OK) {
                        val urls = Foundation.invoke(panel, "URLs")
                        val count = Foundation.invoke(urls, "count").toInt()
                        result = (0 until count).mapNotNull { i ->
                            val url = Foundation.invoke(urls, "objectAtIndex:", i)
                            val nsPath = Foundation.invoke(url, "path")
                            runCatching { Foundation.toStringViaUTF8(nsPath) }.getOrNull()?.let(::File)
                        }
                    }
                }
                result
            } finally {
                pool.drain()
            }
        }
    }
}
