package com.tubetoast.tether.transfer

import java.io.File

fun File.toFileSources(): List<FileSource> = when {
    isDirectory -> JvmFolderWalker().walk(this)
    isFile -> listOf(JvmPathFileSource(toPath()))
    else -> emptyList()
}
