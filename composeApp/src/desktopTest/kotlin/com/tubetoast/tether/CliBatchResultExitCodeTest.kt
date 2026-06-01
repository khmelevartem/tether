package com.tubetoast.tether

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the CLI's user-facing exit-code contract: 0=AllSent, 1=Partial, 2=Failed, 130=Cancelled.
 * Any change to the mapping fails this test, requiring the author to verify the user-facing
 * contract is intentionally updated.
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
