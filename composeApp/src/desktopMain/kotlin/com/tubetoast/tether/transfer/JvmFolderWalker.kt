package com.tubetoast.tether.transfer

import java.io.File
import java.util.ArrayDeque

/** Expects a directory; calling on a non-directory returns an empty list. */
class JvmFolderWalker {
    fun walk(root: File): List<JvmPathFileSource> {
        val results = mutableListOf<JvmPathFileSource>()
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<Pair<File, String>>()
        queue.addLast(root to root.name)

        while (queue.isNotEmpty()) {
            val (dir, prefix) = queue.pollFirst()

            val realPath = try {
                dir.toPath().toRealPath()
            } catch (_: Exception) {
                // Unresolvable symlink — skip without aborting the whole walk.
                continue
            }
            val canonical = realPath.toString()
            if (!visited.add(canonical)) continue

            val entries = dir.listFiles() ?: continue
            for (entry in entries) {
                when {
                    entry.isDirectory -> queue.addLast(entry to "$prefix/${entry.name}")
                    entry.isFile -> {
                        val source = JvmPathFileSource(
                            path = entry.toPath(),
                            relativePath = "$prefix/${entry.name}",
                        )
                        if (HiddenFileFilter.isVisible(source)) results.add(source)
                    }
                }
            }
        }

        return results
    }
}
