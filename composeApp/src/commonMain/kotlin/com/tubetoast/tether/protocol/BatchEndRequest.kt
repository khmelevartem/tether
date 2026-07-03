package com.tubetoast.tether.protocol

import kotlinx.serialization.Serializable

@Serializable
data class BatchEndRequest(
    val batchId: String,
)
