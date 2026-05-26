package com.tubetoast.tether.transfer

interface FilePicker {
    suspend fun pickFiles(): List<FileSource>

    /** Recursively enumerates a folder, filtering hidden files. */
    suspend fun pickFolder(): List<FileSource>

    /**
     * Mobile-only; throws [UnsupportedOperationException] on Desktop.
     */
    suspend fun pickPhotos(): List<FileSource>
}
