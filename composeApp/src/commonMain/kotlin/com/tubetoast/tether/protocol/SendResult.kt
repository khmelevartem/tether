package com.tubetoast.tether.protocol

import kotlinx.serialization.Serializable

@Serializable
sealed class SendResult {
    @Serializable
    data class Success(
        val savedPath: String,
    ) : SendResult()

    @Serializable
    data class Failure(
        val reason: String,
    ) : SendResult()
}
