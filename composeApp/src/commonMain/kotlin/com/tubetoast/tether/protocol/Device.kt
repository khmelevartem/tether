package com.tubetoast.tether.protocol

import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val name: String,
    val host: String,
    val port: Int,
) {
    val id: String get() = "$name@$host:$port"
}
