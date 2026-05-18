package com.tubetoast.tether.network

import java.io.File

internal const val DEFAULT_DOWNLOADS_SUBDIR = "Downloads/Tether"

internal fun resolveDestinationFile(root: File, relativePath: String): File {
    val leafName = relativePath.substringAfterLast('/')
    val parentPath = relativePath.substringBeforeLast('/', "")
    val parentDir = if (parentPath.isEmpty()) root else File(root, parentPath)
    var dest = File(parentDir, leafName)
    if (!dest.exists()) return dest
    val ext = leafName.substringAfterLast('.', "")
    val base = if (ext.isEmpty()) leafName else leafName.removeSuffix(".$ext")
    var i = 1
    do {
        dest = File(parentDir, if (ext.isEmpty()) "${base}_$i" else "${base}_$i.$ext")
        i++
    } while (dest.exists())
    return dest
}
