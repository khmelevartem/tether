package com.tubetoast.tether.transfer

internal class LinuxFilePicker(
    private val windowHolder: WindowHolder,
) : FilePicker {
    override suspend fun pickFiles(): List<FileSource> = awtPickFiles(windowHolder)

    override suspend fun pickFolder(): List<FileSource> = jvmPickFolder(windowHolder)

    override suspend fun pickPhotos(): List<FileSource> =
        throw UnsupportedOperationException("pickPhotos is mobile-only")
}
