package com.tubetoast.tether.transfer

internal class LinuxFilePicker(
    windowHolder: WindowHolder,
) : DesktopFilePicker(windowHolder) {
    override suspend fun pickFiles(): List<FileSource> = awtPickFiles(windowHolder)
}
