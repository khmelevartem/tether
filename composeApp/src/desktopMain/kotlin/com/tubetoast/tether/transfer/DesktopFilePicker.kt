package com.tubetoast.tether.transfer

import com.tubetoast.tether.transfer.mac.MacNativeFilePicker
import com.tubetoast.tether.transfer.win.WindowsNativeFilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import ru.pocketbyte.kydra.log.KydraLog
import ru.pocketbyte.kydra.log.info
import ru.pocketbyte.kydra.log.wrapper.withTag
import java.awt.FileDialog
import javax.swing.JFileChooser

private val log = KydraLog.withTag(default = "DesktopFilePicker")

class DesktopFilePicker(
    private val windowHolder: WindowHolder,
) : FilePicker {
    private val osName = System.getProperty("os.name").orEmpty().lowercase()
    private val isMac = osName.contains("mac")
    private val isWindows = osName.contains("win")

    override suspend fun pickFiles(): List<FileSource> = when {
        isMac -> pickViaNativePanel()
        isWindows -> WindowsNativeFilePicker(windowHolder).pickFiles().flatMap { it.toFileSources() }
        else -> pickViaAwtDialog()
    }

    private suspend fun pickViaNativePanel(): List<FileSource> {
        val selected = MacNativeFilePicker.pickFilesAndFolders()
        log.info { "pickFiles (mac): ${selected.size} item(s) selected" }
        return withContext(Dispatchers.IO) {
            selected.flatMap { it.toFileSources() }
        }
    }

    private suspend fun pickViaAwtDialog(): List<FileSource> {
        val files = withContext(Dispatchers.Swing) {
            val dialog = FileDialog(windowHolder.window as? java.awt.Frame, "Select files", FileDialog.LOAD)
            dialog.isMultipleMode = true
            dialog.isVisible = true
            val selected = dialog.files?.toList() ?: emptyList()
            dialog.dispose()
            selected
        }
        log.info { "pickFiles: ${files.size} file(s) selected" }
        return files.map { JvmPathFileSource(it.toPath()) }
    }

    override suspend fun pickFolder(): List<FileSource> {
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

    override suspend fun pickPhotos(): List<FileSource> =
        throw UnsupportedOperationException("pickPhotos is mobile-only")
}
