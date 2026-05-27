package com.tubetoast.tether.network

import java.io.File

internal actual fun atomicCreateFile(path: String): Boolean = File(path).createNewFile()
