package com.tubetoast.tether.transfer

internal object NoOpFilePicker : FilePicker {
    override suspend fun pickFiles(): List<FileSource> = emptyList()

    override suspend fun pickFolder(): List<FileSource> = emptyList()

    override suspend fun pickPhotos(): List<FileSource> = emptyList()
}
