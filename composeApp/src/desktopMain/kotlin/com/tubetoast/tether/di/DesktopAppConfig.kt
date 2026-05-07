package com.tubetoast.tether.di

import java.io.File

interface DesktopAppConfig : JvmAppConfig

class DefaultDesktopAppConfig(
    override val deviceName: String,
    override val port: Int,
    override val downloadsDir: File = File(System.getProperty("user.home"), "Downloads/Tether"),
) : DesktopAppConfig
