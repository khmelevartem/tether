package com.tubetoast.tether.transfer

private val SYSTEM_FILES = setOf("Thumbs.db", "desktop.ini")

fun isHidden(name: String): Boolean = name.startsWith('.') || name in SYSTEM_FILES
