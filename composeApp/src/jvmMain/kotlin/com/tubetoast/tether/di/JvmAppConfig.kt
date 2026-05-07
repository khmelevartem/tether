package com.tubetoast.tether.di

import java.io.File

interface JvmAppConfig : AppConfig {
    val port: Int
    val downloadsDir: File
}
