package com.tubetoast.tether.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PinCodeGeneratorTest {
    @Test
    fun `result is between 0 and 9999`() {
        val pin = computePinCode(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6))
        assertTrue(pin in 0..9999)
    }

    @Test
    fun `symmetric — same result regardless of key order`() {
        val keyA = byteArrayOf(10, 20, 30, 40)
        val keyB = byteArrayOf(50, 60, 70, 80)
        assertEquals(computePinCode(keyA, keyB), computePinCode(keyB, keyA))
    }

    @Test
    fun `deterministic — known input produces stable code`() {
        // SHA-256([5,7,5]) first 2 bytes: 0x67, 0x27 → (103*256+39) % 10000 = 6407
        assertEquals(6407, computePinCode(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6)))
    }

    @Test
    fun `handles keys of different lengths`() {
        val pin = computePinCode(byteArrayOf(1, 2, 3, 4, 5), byteArrayOf(1, 2))
        assertTrue(pin in 0..9999)
    }

    @Test
    fun `empty keys produce a valid code`() {
        val pin = computePinCode(byteArrayOf(), byteArrayOf())
        assertTrue(pin in 0..9999)
    }
}
