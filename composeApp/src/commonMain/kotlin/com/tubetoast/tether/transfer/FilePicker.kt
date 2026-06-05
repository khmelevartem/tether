package com.tubetoast.tether.transfer

/**
 * User-cancelled pick returns an empty list, never throws.
 */
interface FilePicker {
    suspend fun pickFiles(): List<FileSource>

    /** Recursively enumerates a folder, filtering hidden files. */
    suspend fun pickFolder(): List<FileSource>

    /** Mobile-only; Desktop actual throws [UnsupportedOperationException]. */
    suspend fun pickPhotos(): List<FileSource>
}

enum class PickKind { Files, Folder, Photos }
