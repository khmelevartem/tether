package com.tubetoast.tether.presentation.transfer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProgressFractionTest {
    @Test
    fun nullTotal_returnsNull() {
        assertNull(500L.fractionOf(null))
    }

    @Test
    fun zeroTotal_returnsNull() {
        assertNull(0L.fractionOf(0L))
    }

    @Test
    fun negativeTotal_returnsNull() {
        assertNull(100L.fractionOf(-1L))
    }

    @Test
    fun zeroNumerator_returnsZero() {
        assertEquals(0f, 0L.fractionOf(1_000L))
    }

    @Test
    fun halfway_returnsFiftyPercent() {
        assertEquals(0.5f, 500L.fractionOf(1_000L))
    }

    @Test
    fun overshoot_clampsToOne() {
        assertEquals(1f, 1_500L.fractionOf(1_000L))
    }
}
