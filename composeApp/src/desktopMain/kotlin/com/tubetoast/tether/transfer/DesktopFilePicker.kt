package com.tubetoast.tether.transfer

import java.awt.FileDialog
import java.awt.Frame
import java.io.FilenameFilter
import javax.swing.JFileChooser
import kotlin.io.path.toPath

class DesktopFilePicker : FilePicker {
    override suspend fun pickFiles(multi: Boolean): List<FileSource> {
        val dialog = FileDialog(null as Frame?, "Select files", FileDialog.LOAD).apply {
            isMultipleMode = multi
            filenameFilter = FilenameFilter { _, name -> !isHidden(name) }
            isVisible = true
        }
        return dialog.files.map { JvmFileSource(it.toPath()) }
    }

    override suspend fun pickFolder(): List<FileSource> {
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        val result = chooser.showOpenDialog(null)
        if (result != JFileChooser.APPROVE_OPTION) return emptyList()
        return walk(chooser.selectedFile.toPath())
    }
}
