package com.tubetoast.tether.protocol

import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
)
