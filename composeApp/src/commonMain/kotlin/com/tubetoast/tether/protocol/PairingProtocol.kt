package com.tubetoast.tether.protocol

import kotlinx.serialization.Serializable

@Serializable
data class PairRequest(
    val publicKey: ByteArray,
    val deviceName: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PairRequest) return false
        return publicKey.contentEquals(other.publicKey) && deviceName == other.deviceName
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + deviceName.hashCode()
        return result
    }
}

@Serializable
data class PairResponse(
    val publicKey: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PairResponse) return false
        return publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int = publicKey.contentHashCode()
}
