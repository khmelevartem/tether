package com.tubetoast.tether.protocol

import kotlinx.serialization.Serializable

@Serializable
data class BatchCancelRequest(
    val batchId: String,
)
