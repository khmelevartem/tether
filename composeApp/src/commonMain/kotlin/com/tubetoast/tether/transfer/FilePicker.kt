package com.tubetoast.tether.transfer

interface FilePicker {
    suspend fun pickFiles(multi: Boolean): List<FileSource>

    suspend fun pickFolder(): List<FileSource>
}
