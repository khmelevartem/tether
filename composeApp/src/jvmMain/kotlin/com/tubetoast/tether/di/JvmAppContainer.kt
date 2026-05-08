package com.tubetoast.tether.di

import com.tubetoast.tether.network.FileServer
import java.io.File

abstract class JvmAppContainer(
    config: JvmAppConfig,
) : AppContainer(config) {
    val downloadsDir: File = config.downloadsDir
    override val fileServer: FileServer = FileServer(config.port, downloadsDir)
}
