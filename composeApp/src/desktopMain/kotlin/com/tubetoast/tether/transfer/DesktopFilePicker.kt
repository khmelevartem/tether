package com.tubetoast.tether.transfer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.FilenameFilter
import javax.swing.JFileChooser
import kotlin.io.path.toPath

class DesktopFilePicker : FilePicker {
    override suspend fun pickFiles(multi: Boolean): List<FileSource> {
        val dialog = withContext(Dispatchers.Swing) {
            FileDialog(null as Frame?, "Select files", FileDialog.LOAD).apply {
                isMultipleMode = multi
                filenameFilter = FilenameFilter { _, name -> !isHidden(name) }
                isVisible = true
            }
        }
        return withContext(Dispatchers.IO) {
            dialog.files.map { JvmFileSource(it.toPath()) }
        }
    }

    override suspend fun pickFolder(): List<FileSource> {
        val (result, selectedFile) = withContext(Dispatchers.Swing) {
            val chooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                isAcceptAllFileFilterUsed = false
            }
            chooser.showOpenDialog(null) to chooser.selectedFile
        }
        if (result != JFileChooser.APPROVE_OPTION) return emptyList()
        return withContext(Dispatchers.IO) {
            walk(selectedFile.toPath())
        }
    }
}
