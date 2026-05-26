package com.tubetoast.tether.transfer

object HiddenFileFilter {
    private val hiddenNames = setOf("Thumbs.db", ".DS_Store", "desktop.ini")

    fun isVisible(source: FileSource): Boolean {
        val name = source.name
        return !name.startsWith(".") && name !in hiddenNames
    }
}
