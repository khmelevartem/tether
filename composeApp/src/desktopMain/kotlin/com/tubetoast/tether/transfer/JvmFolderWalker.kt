package com.tubetoast.tether.transfer

import java.io.IOException
import java.nio.file.FileSystemLoopException
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

fun walk(root: Path): List<FileSource> {
    val visitedKeys = mutableSetOf<Any>()
    val result = mutableListOf<FileSource>()
    Files.walkFileTree(
        root,
        setOf(FileVisitOption.FOLLOW_LINKS),
        Int.MAX_VALUE,
        object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val key = attrs.fileKey() ?: dir.toRealPath()
                if (!visitedKeys.add(key)) return FileVisitResult.SKIP_SUBTREE
                if (dir != root && isHidden(dir.fileName.toString())) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val key = attrs.fileKey() ?: file.toRealPath()
                if (!visitedKeys.add(key)) return FileVisitResult.CONTINUE
                if (isHidden(file.fileName.toString())) return FileVisitResult.CONTINUE
                val relative = root.relativize(file).toString().replace('\\', '/')
                result += JvmFileSource(file, relativePath = relative)
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                if (exc is FileSystemLoopException) return FileVisitResult.SKIP_SUBTREE
                return FileVisitResult.CONTINUE
            }
        },
    )
    return result
}
