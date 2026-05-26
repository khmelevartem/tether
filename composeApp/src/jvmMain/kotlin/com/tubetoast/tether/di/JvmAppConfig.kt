package com.tubetoast.tether.di

import com.tubetoast.tether.security.DeviceKeyPair
import java.io.File

interface JvmAppConfig : AppConfig {
    val port: Int
    val downloadsDir: File
    val deviceKeyPair: DeviceKeyPair
}
