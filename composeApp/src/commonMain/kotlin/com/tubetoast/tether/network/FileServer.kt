package com.tubetoast.tether.network

expect class FileServer {
    /** The OS-assigned port after [start] is called; -1 before start. */
    val port: Int

    fun start(): Int

    fun stop()
}
