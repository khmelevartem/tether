package com.tubetoast.tether.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceNameValidatorTest {
    @Test
    fun `empty string rejected`() {
        assertTrue(DeviceNameValidator.validate("").isFailure)
    }

    @Test
    fun `whitespace-only rejected`() {
        assertTrue(DeviceNameValidator.validate("   ").isFailure)
        assertTrue(DeviceNameValidator.validate("\t\n").isFailure)
    }

    @Test
    fun `leading and trailing whitespace trimmed`() {
        val result = DeviceNameValidator.validate("  Alice  ")
        assertTrue(result.isSuccess)
        assertEquals("Alice", result.getOrThrow())
    }

    @Test
    fun `exactly 50 code points accepted`() {
        val name = "a".repeat(50)
        assertTrue(DeviceNameValidator.validate(name).isSuccess)
    }

    @Test
    fun `51 code points rejected`() {
        val name = "a".repeat(51)
        assertTrue(DeviceNameValidator.validate(name).isFailure)
    }

    @Test
    fun `surrogate pair emoji counts as one code point`() {
        // U+1F600 GRINNING FACE — 2 Char values, 1 code point.
        val emoji = "😀"
        val name = emoji.repeat(50)
        assertTrue(DeviceNameValidator.validate(name).isSuccess)
    }

    @Test
    fun `51 emoji code points rejected`() {
        val emoji = "😀"
        val name = emoji.repeat(51)
        assertTrue(DeviceNameValidator.validate(name).isFailure)
    }

    @Test
    fun `valid name returned trimmed`() {
        val result = DeviceNameValidator.validate("Bob")
        assertEquals("Bob", result.getOrThrow())
    }

    @Test
    fun `49 ascii chars plus one surrogate pair emoji accepted`() {
        val name = "a".repeat(49) + "😀"
        assertTrue(DeviceNameValidator.validate(name).isSuccess)
    }

    @Test
    fun `50 ascii chars plus one surrogate pair emoji rejected`() {
        val name = "a".repeat(50) + "😀"
        assertTrue(DeviceNameValidator.validate(name).isFailure)
    }
}
