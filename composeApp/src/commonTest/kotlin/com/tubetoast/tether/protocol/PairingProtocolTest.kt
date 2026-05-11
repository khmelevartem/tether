package com.tubetoast.tether.protocol

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PairingProtocolTest {
    private val json = Json { prettyPrint = false }

    @Test
    fun `PairRequest equals when publicKey and deviceName match`() {
        val a = PairRequest(publicKey = byteArrayOf(1, 2, 3), deviceName = "Alice")
        val b = PairRequest(publicKey = byteArrayOf(1, 2, 3), deviceName = "Alice")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `PairRequest not equal when publicKey differs`() {
        val a = PairRequest(publicKey = byteArrayOf(1, 2, 3), deviceName = "Alice")
        val b = PairRequest(publicKey = byteArrayOf(4, 5, 6), deviceName = "Alice")
        assertFalse(a == b)
    }

    @Test
    fun `PairRequest round-trips through JSON`() {
        val original = PairRequest(publicKey = byteArrayOf(-128, 0, 127), deviceName = "Bob")
        val decoded = json.decodeFromString<PairRequest>(json.encodeToString(PairRequest.serializer(), original))
        assertEquals(original, decoded)
    }

    @Test
    fun `PairResponse equals when publicKey matches`() {
        val a = PairResponse(publicKey = byteArrayOf(10, 20, 30))
        val b = PairResponse(publicKey = byteArrayOf(10, 20, 30))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `PairResponse not equal when publicKey differs`() {
        val a = PairResponse(publicKey = byteArrayOf(1, 2, 3))
        val b = PairResponse(publicKey = byteArrayOf(4, 5, 6))
        assertFalse(a == b)
    }

    @Test
    fun `PairResponse round-trips through JSON`() {
        val original = PairResponse(publicKey = byteArrayOf(-128, 0, 127))
        val decoded = json.decodeFromString<PairResponse>(json.encodeToString(PairResponse.serializer(), original))
        assertEquals(original, decoded)
    }

    @Test
    fun `PairRequest with empty publicKey round-trips through JSON`() {
        val original = PairRequest(publicKey = byteArrayOf(), deviceName = "Empty")
        val decoded = json.decodeFromString<PairRequest>(json.encodeToString(PairRequest.serializer(), original))
        assertEquals(original, decoded)
    }

    @Test
    fun `PairResponse with empty publicKey round-trips through JSON`() {
        val original = PairResponse(publicKey = byteArrayOf())
        val decoded = json.decodeFromString<PairResponse>(json.encodeToString(PairResponse.serializer(), original))
        assertEquals(original, decoded)
    }
}
