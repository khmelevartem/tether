package com.tubetoast.tether.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PeerAnnouncement(
    val alias: String,
    val fingerprint: String,
    val port: Int,
    val deviceType: DeviceType,
    val version: Int = 1,
)

@Serializable
enum class DeviceType {
    @SerialName("android")
    Android,

    @SerialName("ios")
    Ios,

    @SerialName("desktop")
    Desktop,

    @SerialName("cli")
    Cli,
}
