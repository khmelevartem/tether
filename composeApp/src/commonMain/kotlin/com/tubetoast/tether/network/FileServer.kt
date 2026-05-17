package com.tubetoast.tether.network

expect class FileServer {
    fun start(): Int

    fun stop()
}
