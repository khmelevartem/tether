package com.tubetoast.tether.network

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CliPairingConfirmationHandlerTest {
    @Test
    fun `confirmPairing prints zero-padded PIN`() = runTest {
        val lines = mutableListOf<String>()
        val handler = CliPairingConfirmationHandler(output = lines::add, input = { "n" })
        handler.confirmPairing(42, "Alice")
        assertTrue(lines.any { it.contains("0042") }, "PIN must be zero-padded to 4 digits")
    }

    @Test
    fun `confirmPairing returns true for y input`() = runTest {
        val handler = CliPairingConfirmationHandler(output = {}, input = { "y" })
        assertTrue(handler.confirmPairing(1234, "Bob"))
    }

    @Test
    fun `confirmPairing returns true for uppercase Y`() = runTest {
        val handler = CliPairingConfirmationHandler(output = {}, input = { "Y" })
        assertTrue(handler.confirmPairing(1234, "Bob"))
    }

    @Test
    fun `confirmPairing returns false for n input`() = runTest {
        val handler = CliPairingConfirmationHandler(output = {}, input = { "n" })
        assertFalse(handler.confirmPairing(1234, "Bob"))
    }

    @Test
    fun `confirmPairing returns false for null input (EOF)`() = runTest {
        val handler = CliPairingConfirmationHandler(output = {}, input = { null })
        assertFalse(handler.confirmPairing(1234, "Bob"))
    }

    @Test
    fun `confirmPairing includes remoteName in output`() = runTest {
        val lines = mutableListOf<String>()
        val handler = CliPairingConfirmationHandler(output = lines::add, input = { "n" })
        handler.confirmPairing(9999, "Alice")
        assertTrue(lines.any { it.contains("'Alice'") }, "output must include remote name in single quotes")
    }

    @Test
    fun `confirmPairing formats single-digit PIN with three leading zeros`() = runTest {
        val lines = mutableListOf<String>()
        val handler = CliPairingConfirmationHandler(output = lines::add, input = { "n" })
        handler.confirmPairing(7, "Device")
        assertTrue(lines.any { it.contains("0007") }, "single-digit PIN must have three leading zeros")
        assertEquals(1, lines.count { it.contains("0007") })
    }
}
