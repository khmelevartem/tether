package com.tubetoast.tether.transfer

import kotlinx.serialization.Serializable

@Serializable
data class PeerIdentity(
    val id: String,
)
