package com.tubetoast.tether.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InfoDto(
    val alias: String,
    val fingerprint: String,
    val port: Int,
    val deviceType: DeviceType,
    val version: Int = 1,
)

@Serializable
enum class DeviceType {
    @SerialName("mobile")
    Mobile,

    @SerialName("desktop")
    Desktop,

    @SerialName("web")
    Web,

    @SerialName("headless")
    Headless,

    @SerialName("server")
    Server,
}
