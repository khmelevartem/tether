@file:OptIn(ExperimentalForeignApi::class)

package com.tubetoast.tether.network

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.EEXIST
import platform.posix.errno
import platform.posix.fclose
import platform.posix.fopen

internal actual fun atomicCreateFile(path: String): Boolean {
    val file = fopen(path, "wbx")
    if (file != null) {
        fclose(file)
        return true
    }
    if (errno == EEXIST) return false
    throw IOException("FileServer: could not create placeholder '$path'")
}
