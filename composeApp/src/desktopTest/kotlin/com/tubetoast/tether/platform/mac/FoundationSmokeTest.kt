package com.tubetoast.tether.platform.mac

import com.tubetoast.tether.platform.mac.Foundation
import org.junit.Assume
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class FoundationSmokeTest {
    @Test
    fun `NSOpenPanel class resolves via objc runtime`() {
        Assume.assumeTrue(System.getProperty("os.name").lowercase().contains("mac"))

        val cls = Foundation.getObjcClass("NSOpenPanel")
        assertNotNull(cls, "NSOpenPanel class must be non-null")
        assertFalse(Foundation.isNil(cls), "NSOpenPanel class must not be NIL")
    }

    @Test
    fun `NSThread isMainThread round-trips without crashing`() {
        Assume.assumeTrue(System.getProperty("os.name").lowercase().contains("mac"))

        // Exercises the arm64-safe objc_msgSend → invokeLong path and BOOL return marshalling.
        // Either true or false is acceptable; the point is it doesn't crash.
        val result = Foundation.invoke("NSThread", "isMainThread")
        val value = result.toInt()
        assert(value == 0 || value == 1) { "Expected 0 or 1, got $value" }
    }
}
