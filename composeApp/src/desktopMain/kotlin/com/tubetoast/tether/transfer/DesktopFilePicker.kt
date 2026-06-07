package com.tubetoast.tether.transfer

import com.tubetoast.tether.foundation.DesktopHostOs
import com.tubetoast.tether.foundation.currentHostOs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.wrapper.withTag
import javax.swing.JFileChooser

private val log = KydraLog.withTag(default = "DesktopFilePicker")

internal abstract class DesktopFilePicker(
    protected val windowHolder: WindowHolder,
) : FilePicker {
    final override suspend fun pickFolder(): List<FileSource> {
        val dir = withContext(Dispatchers.Swing) {
            val chooser = JFileChooser()
            chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            val result = chooser.showOpenDialog(windowHolder.window)
            if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
        }
        if (dir == null) {
            log.info { "pickFolder: cancelled" }
            return emptyList()
        }
        val sources = withContext(Dispatchers.IO) { JvmFolderWalker().walk(dir) }
        log.info { "pickFolder: ${sources.size} file(s) from ${dir.name}" }
        return sources
    }

    final override suspend fun pickPhotos(): List<FileSource> =
        throw UnsupportedOperationException("pickPhotos is mobile-only")
}

internal fun desktopFilePicker(windowHolder: WindowHolder): FilePicker = when (currentHostOs) {
    DesktopHostOs.MacOs -> MacFilePicker(windowHolder)
    DesktopHostOs.Windows -> WindowsFilePicker(windowHolder)
    DesktopHostOs.Linux -> LinuxFilePicker(windowHolder)
}
