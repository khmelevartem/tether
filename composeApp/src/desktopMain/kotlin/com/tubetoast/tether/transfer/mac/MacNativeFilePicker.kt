// Adapted from FileKit (MIT, github.com/vinceglb/FileKit), whose Foundation bridge derives from IntelliJ Community (Apache-2.0).
package com.tubetoast.tether.transfer.mac

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal object MacNativeFilePicker {
    private const val NS_MODAL_RESPONSE_OK = 1

    /** Native NSOpenPanel allowing BOTH files and folders, multi-select. Empty list on cancel. */
    suspend fun pickFilesAndFolders(): List<File> = withContext(Dispatchers.IO) {
        val pool = Foundation.NSAutoreleasePool()
        try {
            var result: List<File> = emptyList()
            Foundation.executeOnMainThread(withAutoreleasePool = false, waitUntilDone = true) {
                val panel = Foundation.invoke("NSOpenPanel", "new")
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
                        Foundation.toStringViaUTF8(nsPath)?.let(::File)
                    }
                }
            }
            result
        } finally {
            pool.drain()
        }
    }
}
