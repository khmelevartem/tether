package com.tubetoast.tether

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the exit-code contract from issue #328's DoD (0=AllSent, 1=Partial, 2=Failed, 130=Cancelled)
 * so that future edits to the mapping fail here and require explicit re-confirmation against the
 * user-facing contract, not just a passing build.
 */
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
