package com.tubetoast.tether.protocol

import kotlinx.serialization.Serializable

@Serializable
data class PeerIdentity(
    val id: String,
)
