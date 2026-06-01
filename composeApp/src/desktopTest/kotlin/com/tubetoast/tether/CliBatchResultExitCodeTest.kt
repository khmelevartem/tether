package com.tubetoast.tether

import kotlin.test.Test
import kotlin.test.assertEquals

class CliBatchResultExitCodeTest {
    @Test
    fun `AllSent maps to exit code 0`() = assertEquals(0, CliBatchResult.AllSent.toExitCode())

    @Test
    fun `Partial maps to exit code 1`() = assertEquals(1, CliBatchResult.Partial.toExitCode())

    @Test
    fun `Failed maps to exit code 2`() = assertEquals(2, CliBatchResult.Failed.toExitCode())

    @Test
    fun `Cancelled maps to exit code 130`() = assertEquals(130, CliBatchResult.Cancelled.toExitCode())
}
