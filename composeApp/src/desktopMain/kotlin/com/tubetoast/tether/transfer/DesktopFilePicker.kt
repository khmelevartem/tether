package com.tubetoast.tether.transfer

import com.tubetoast.tether.foundation.DesktopHostOs
import com.tubetoast.tether.foundation.currentHostOs

internal abstract class DesktopFilePicker(
    protected val windowHolder: WindowHolder,
) : FilePicker {
    // Desktop folders arrive via drag-and-drop (and on macOS via the combined pickFiles dialog);
    // the mobile chooser sheet that would trigger this is disabled on Desktop.
    final override suspend fun pickFolder(): List<FileSource> =
        throw UnsupportedOperationException("pickFolder is not used on Desktop")

    final override suspend fun pickPhotos(): List<FileSource> =
        throw UnsupportedOperationException("pickPhotos is mobile-only")
}

internal fun desktopFilePicker(windowHolder: WindowHolder): FilePicker = when (currentHostOs) {
    DesktopHostOs.MacOs -> MacFilePicker(windowHolder)
    DesktopHostOs.Windows -> WindowsFilePicker(windowHolder)
    DesktopHostOs.Linux -> LinuxFilePicker(windowHolder)
}
