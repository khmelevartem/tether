package com.tubetoast.tether.transfer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.wrapper.withTag
import java.awt.FileDialog

private val log = KydraLog.withTag(default = "AwtFilePicker")

internal suspend fun awtPickFiles(windowHolder: WindowHolder): List<FileSource> {
    val files = withContext(Dispatchers.Swing) {
        val dialog = FileDialog(windowHolder.window as? java.awt.Frame, "Select files", FileDialog.LOAD)
        dialog.isMultipleMode = true
        dialog.isVisible = true
        val selected = dialog.files?.toList() ?: emptyList()
        dialog.dispose()
        selected
    }
    log.info { "pickFiles (awt): ${files.size} file(s) selected" }
    return files.map { JvmPathFileSource(it.toPath()) }
}
