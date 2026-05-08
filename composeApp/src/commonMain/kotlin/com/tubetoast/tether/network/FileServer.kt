package com.tubetoast.tether.network

/**
 * The expect declaration intentionally omits a constructor: JVM's `actual`
 * takes `(port, downloadsDir)`, Apple's stub takes no args. KMP allows this
 * — actuals are free to declare their own constructors when expect declares
 * none. Common code never instantiates `FileServer` directly; only the
 * platform-specific `*AppContainer` does, and it knows its own constructor.
 */
expect class FileServer {
    fun start(): Int

    fun stop()
}
