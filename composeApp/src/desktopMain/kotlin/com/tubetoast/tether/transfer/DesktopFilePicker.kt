package com.tubetoast.tether.transfer

import com.tubetoast.tether.foundation.DesktopHostOs
import com.tubetoast.tether.foundation.currentHostOs

internal fun desktopFilePicker(windowHolder: WindowHolder): FilePicker = when (currentHostOs) {
    DesktopHostOs.MacOs -> MacFilePicker(windowHolder)
    DesktopHostOs.Windows -> WindowsFilePicker(windowHolder)
    DesktopHostOs.Linux -> LinuxFilePicker(windowHolder)
}
