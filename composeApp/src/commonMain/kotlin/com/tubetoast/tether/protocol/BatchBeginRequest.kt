package com.tubetoast.tether.protocol

import kotlinx.serialization.Serializable

@Serializable
data class BatchBeginRequest(
    val batchId: String,
    val totalFiles: Int,
    val totalBytes: Long? = null,
)
