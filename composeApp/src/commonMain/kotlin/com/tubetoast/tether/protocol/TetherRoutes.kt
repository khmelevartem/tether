package com.tubetoast.tether.protocol

/** Wire route paths shared by [com.tubetoast.tether.network.FileClient] and the file-server routing. */
object TetherRoutes {
    const val HEALTH = "/health"
    const val HELLO = "/hello"
    const val PAIR = "/pair"
    const val UPLOAD = "/upload"
    const val BATCH_BEGIN = "/batch-begin"
    const val BATCH_CANCEL = "/batch-cancel"
    const val BATCH_END = "/batch-end"
}
