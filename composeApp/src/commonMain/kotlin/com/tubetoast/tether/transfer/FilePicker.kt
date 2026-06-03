package com.tubetoast.tether.transfer

/**
 * User-cancelled pick returns an empty list, never throws.
 * [pickPhotos] is mobile-only; Desktop actual throws [UnsupportedOperationException].
 */
interface FilePicker {
    suspend fun pickFiles(): List<FileSource>

    /** Recursively enumerates a folder, filtering hidden files. */
    suspend fun pickFolder(): List<FileSource>

    suspend fun pickPhotos(): List<FileSource>
}

enum class PickKind { Files, Folder, Photos }
